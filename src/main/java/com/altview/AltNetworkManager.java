package com.altview;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class AltNetworkManager {
    public static final int PORT_ALT_1 = 25581;
    public static final int PORT_ALT_2 = 25582;

    private static volatile DataOutputStream alt1CommandOut;
    private static volatile DataOutputStream alt2CommandOut;

    public static final Identifier ALT1_TEX_ID = Identifier.of("altview", "alt1_screen");
    public static final Identifier ALT2_TEX_ID = Identifier.of("altview", "alt2_screen");
    private static NativeImageBackedTexture alt1Texture;
    private static NativeImageBackedTexture alt2Texture;

    public record RawFrame(int width, int height, int[] pixels) {}

    public static volatile RawFrame pendingAlt1Frame = null;
    public static volatile RawFrame pendingAlt2Frame = null;

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean capturing = new AtomicBoolean(false);
    private static ServerSocket serverSocket1;
    private static ServerSocket serverSocket2;

    public static void restartNetworking() {
        stop();
        running.set(true);

        if (ModConfig.INSTANCE.clientRole == ModConfig.Role.MAIN) {
            startMainReceiver(PORT_ALT_1, 1);
            startMainReceiver(PORT_ALT_2, 2);
        } else {
            int targetPort = (ModConfig.INSTANCE.clientRole == ModConfig.Role.ALT_1) ? PORT_ALT_1 : PORT_ALT_2;
            startAltWorker(targetPort);
        }
    }

    public static void stop() {
        running.set(false);
        try { if (serverSocket1 != null) serverSocket1.close(); } catch (Exception ignored) {}
        try { if (serverSocket2 != null) serverSocket2.close(); } catch (Exception ignored) {}
    }

    private static void startMainReceiver(int port, int altIndex) {
        Thread receiverThread = new Thread(() -> {
            while (running.get() && ModConfig.INSTANCE.clientRole == ModConfig.Role.MAIN) {
                try (ServerSocket server = new ServerSocket(port)) {
                    if (altIndex == 1) serverSocket1 = server;
                    else serverSocket2 = server;

                    Socket client = server.accept();
                    client.setTcpNoDelay(true);

                    DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()));
                    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()));

                    if (altIndex == 1) alt1CommandOut = out;
                    else alt2CommandOut = out;

                    while (running.get() && !client.isClosed()) {
                        int w = in.readInt();
                        int h = in.readInt();
                        int byteLen = in.readInt();
                        if (w > 0 && h > 0 && byteLen > 0 && byteLen < 5_000_000) {
                            byte[] compressed = new byte[byteLen];
                            in.readFully(compressed);

                            int[] pixels = new int[w * h];
                            try (DataInputStream zIn = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed)))) {
                                for (int i = 0; i < pixels.length; i++) {
                                    pixels[i] = zIn.readInt();
                                }
                            }
                            RawFrame frame = new RawFrame(w, h, pixels);
                            if (altIndex == 1) pendingAlt1Frame = frame;
                            else pendingAlt2Frame = frame;
                        }
                    }
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }, "AltView-MainReceiver-" + altIndex);
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private static void startAltWorker(int port) {
        Thread altThread = new Thread(() -> {
            while (running.get() && ModConfig.INSTANCE.clientRole != ModConfig.Role.MAIN) {
                String host = (ModConfig.INSTANCE.targetHostIp == null || ModConfig.INSTANCE.targetHostIp.isBlank())
                        ? "127.0.0.1" : ModConfig.INSTANCE.targetHostIp.trim();
                try (Socket socket = new Socket(host, port)) {
                    socket.setTcpNoDelay(true);
                    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                    DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

                    Thread cmdReader = new Thread(() -> {
                        try {
                            while (running.get() && !socket.isClosed()) {
                                byte cmd = in.readByte();
                                if (cmd == 1) {
                                    MinecraftClient.getInstance().execute(AltNetworkManager::triggerRightClickOnAlt);
                                }
                            }
                        } catch (Exception ignored) {}
                    }, "AltView-CmdReader");
                    cmdReader.setDaemon(true);
                    cmdReader.start();

                    while (running.get() && !socket.isClosed() && ModConfig.INSTANCE.clientRole != ModConfig.Role.MAIN) {
                        long start = System.currentTimeMillis();
                        byte[] packet = AltViewClient.latestCompressedPacket;
                        if (packet != null) {
                            AltViewClient.latestCompressedPacket = null;
                            out.write(packet);
                            out.flush();
                        }
                        int sleepMs = Math.max(15, 1000 / Math.max(1, ModConfig.INSTANCE.captureFps));
                        long elapsed = System.currentTimeMillis() - start;
                        if (elapsed < sleepMs) {
                            Thread.sleep(sleepMs - elapsed);
                        }
                    }
                } catch (Exception e) {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                }
            }
        }, "AltView-AltSocket");
        altThread.setDaemon(true);
        altThread.start();
    }

    public static void sendRightClickCommand(int altIndex) {
        Thread sender = new Thread(() -> {
            try {
                DataOutputStream out = (altIndex == 1) ? alt1CommandOut : alt2CommandOut;
                if (out != null) {
                    synchronized (out) {
                        out.writeByte(1);
                        out.flush();
                    }
                }
            } catch (Exception ignored) {}
        });
        sender.setDaemon(true);
        sender.start();
    }

    private static void triggerRightClickOnAlt() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        HitResult hit = mc.crosshairTarget;
        for (Hand hand : Hand.values()) {
            if (hit != null) {
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    ActionResult res = mc.interactionManager.interactBlock(mc.player, hand, blockHit);
                    if (res.isAccepted()) {
                        mc.player.swingHand(hand);
                        return;
                    }
                } else if (hit.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHit = (EntityHitResult) hit;
                    ActionResult res = mc.interactionManager.interactEntity(mc.player, entityHit.getEntity(), hand);
                    if (res.isAccepted()) {
                        mc.player.swingHand(hand);
                        return;
                    }
                }
            }
            ActionResult itemRes = mc.interactionManager.interactItem(mc.player, hand);
            if (itemRes.isAccepted()) {
                mc.player.swingHand(hand);
                return;
            }
        }
    }

    public static void captureFrameAsync(MinecraftClient mc) {
        if (mc.getWindow().isMinimized() || !capturing.compareAndSet(false, true)) return;

        int factor = Math.max(1, ModConfig.INSTANCE.downscaleFactor);
        ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), factor, rawImage -> {
            Thread compressThread = new Thread(() -> {
                try (NativeImage img = rawImage) {
                    int w = img.getWidth();
                    int h = img.getHeight();

                    ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
                    Deflater deflater = new Deflater(Math.max(1, Math.min(9, ModConfig.INSTANCE.compressionLevel)));
                    try (DataOutputStream zOut = new DataOutputStream(new DeflaterOutputStream(compressedBaos, deflater))) {
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                int argb = img.getColorArgb(x, y) & 0xFFF8F8F8;
                                zOut.writeInt(argb | 0xFF000000);
                            }
                        }
                    } finally {
                        deflater.end();
                    }

                    byte[] compBytes = compressedBaos.toByteArray();
                    ByteArrayOutputStream packetBaos = new ByteArrayOutputStream(12 + compBytes.length);
                    DataOutputStream packetOut = new DataOutputStream(packetBaos);
                    packetOut.writeInt(w);
                    packetOut.writeInt(h);
                    packetOut.writeInt(compBytes.length);
                    packetOut.write(compBytes);

                    AltViewClient.latestCompressedPacket = packetBaos.toByteArray();
                } catch (Exception ignored) {
                } finally {
                    capturing.set(false);
                }
            }, "AltView-Compressor");
            compressThread.setDaemon(true);
            compressThread.start();
        });
    }

    public static void updateTexturesOnRenderThread() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (pendingAlt1Frame != null) {
            RawFrame frame = pendingAlt1Frame;
            pendingAlt1Frame = null;
            alt1Texture = applyFrameToTexture(mc, frame, alt1Texture, ALT1_TEX_ID, "altview_alt1");
        }
        if (pendingAlt2Frame != null) {
            RawFrame frame = pendingAlt2Frame;
            pendingAlt2Frame = null;
            alt2Texture = applyFrameToTexture(mc, frame, alt2Texture, ALT2_TEX_ID, "altview_alt2");
        }
    }

    private static NativeImageBackedTexture applyFrameToTexture(
            MinecraftClient mc, RawFrame frame, NativeImageBackedTexture tex, Identifier id, String name) {
        try {
            if (tex == null || tex.getImage() == null || tex.getImage().getWidth() != frame.width() || tex.getImage().getHeight() != frame.height()) {
                if (tex != null) tex.close();
                NativeImage newImg = new NativeImage(frame.width(), frame.height(), false);
                tex = new NativeImageBackedTexture(() -> name, newImg);
                mc.getTextureManager().registerTexture(id, tex);
            }
            NativeImage target = tex.getImage();
            int i = 0;
            for (int y = 0; y < frame.height(); y++) {
                for (int x = 0; x < frame.width(); x++) {
                    target.setColorArgb(x, y, frame.pixels()[i++]);
                }
            }
            tex.upload();
        } catch (Exception ignored) {}
        return tex;
    }

    public static boolean hasAlt1Texture() { return alt1Texture != null; }
    public static boolean hasAlt2Texture() { return alt2Texture != null; }
}