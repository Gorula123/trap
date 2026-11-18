package com.altview;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AltViewClient implements ClientModInitializer {
    public static KeyBinding triggerAlt1RmbKey;
    public static KeyBinding triggerAlt2RmbKey;
    public static KeyBinding toggleHudKey;
    public static KeyBinding openConfigKey;

    public static volatile byte[] latestCompressedPacket = null;
    private long lastCaptureTime = 0;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        AltNetworkManager.restartNetworking();

        triggerAlt1RmbKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "ПКМ на Твинке 1 (Стазис / Ловушка)",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "AltView & Stasis"
        ));

        triggerAlt2RmbKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "ПКМ на Твинке 2 (Стазис / Ловушка)",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "AltView & Stasis"
        ));

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Скрыть/Показать экраны твинков",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "AltView & Stasis"
        ));

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Меню настроек AltView",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "AltView & Stasis"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                client.setScreen(new ConfigScreen(client.currentScreen));
            }

            while (toggleHudKey.wasPressed()) {
                ModConfig.INSTANCE.showPiP = !ModConfig.INSTANCE.showPiP;
                ModConfig.save();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§b[AltView] §fОкна твинков: " + (ModConfig.INSTANCE.showPiP ? "§aПоказаны" : "§cСкрыты")), true);
                }
            }

            if (ModConfig.INSTANCE.clientRole == ModConfig.Role.MAIN) {
                while (triggerAlt1RmbKey.wasPressed()) {
                    AltNetworkManager.sendRightClickCommand(1);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("§a[AltView] §fПКМ отправлен -> Твинк 1"), true);
                    }
                }
                while (triggerAlt2RmbKey.wasPressed()) {
                    AltNetworkManager.sendRightClickCommand(2);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("§a[AltView] §fПКМ отправлен -> Твинк 2"), true);
                    }
                }
            } else {
                if (client.options.pauseOnLostFocus) {
                    client.options.pauseOnLostFocus = false;
                }
                if (ModConfig.INSTANCE.lockAltCamera && client.player != null) {
                    client.player.setYaw(ModConfig.INSTANCE.lockedYaw);
                    client.player.setPitch(ModConfig.INSTANCE.lockedPitch);
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (ModConfig.INSTANCE.clientRole == ModConfig.Role.MAIN) {
                AltNetworkManager.updateTexturesOnRenderThread();
                if (!ModConfig.INSTANCE.showPiP) return;

                if (AltNetworkManager.hasAlt1Texture()) {
                    int x = ModConfig.INSTANCE.alt1X;
                    int y = ModConfig.INSTANCE.alt1Y;
                    int w = ModConfig.INSTANCE.alt1Width;
                    int h = ModConfig.INSTANCE.alt1Height;

                    drawContext.fill(x - 2, y - 14, x + w + 2, y + h + 2, 0x90000000);
                    drawContext.drawTextWithShadow(mc.textRenderer, "Твинк 1 [V]", x + 2, y - 11, 0xFF55FF55);
                    drawContext.drawTexture(
                            RenderPipelines.GUI_TEXTURED,
                            AltNetworkManager.ALT1_TEX_ID,
                            x, y, 0.0f, 0.0f, w, h, w, h
                    );
                }

                if (AltNetworkManager.hasAlt2Texture()) {
                    int x = ModConfig.INSTANCE.alt2X;
                    int y = ModConfig.INSTANCE.alt2Y;
                    int w = ModConfig.INSTANCE.alt2Width;
                    int h = ModConfig.INSTANCE.alt2Height;

                    drawContext.fill(x - 2, y - 14, x + w + 2, y + h + 2, 0x90000000);
                    drawContext.drawTextWithShadow(mc.textRenderer, "Твинк 2 [B]", x + 2, y - 11, 0xFF55FFFF);
                    drawContext.drawTexture(
                            RenderPipelines.GUI_TEXTURED,
                            AltNetworkManager.ALT2_TEX_ID,
                            x, y, 0.0f, 0.0f, w, h, w, h
                    );
                }
            } else {
                long now = System.currentTimeMillis();
                long interval = 1000 / Math.max(1, ModConfig.INSTANCE.captureFps);
                if (now - lastCaptureTime >= interval) {
                    lastCaptureTime = now;
                    AltNetworkManager.captureFrameAsync(mc);
                }
            }
        });
    }
}
