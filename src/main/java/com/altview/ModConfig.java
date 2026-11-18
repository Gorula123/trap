package com.altview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    public enum Role {
        MAIN("Основа (Приём экрана)"),
        ALT_1("Твинк 1 (Порт 25581)"),
        ALT_2("Твинк 2 (Порт 25582)");

        public final String displayName;
        Role(String displayName) { this.displayName = displayName; }
    }

    public Role clientRole = Role.MAIN;
    public boolean showPiP = true;
    public String targetHostIp = "127.0.0.1";

    public int downscaleFactor = 2;
    public int compressionLevel = 4;
    public int captureFps = 15;

    public int alt1X = 10;
    public int alt1Y = 18;
    public int alt1Width = 220;
    public int alt1Height = 124;

    public int alt2X = 10;
    public int alt2Y = 160;
    public int alt2Width = 220;
    public int alt2Height = 124;

    public boolean lockAltCamera = false;
    public float lockedYaw = 0.0f;
    public float lockedPitch = 90.0f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "altview.json");
    public static ModConfig INSTANCE = new ModConfig();

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}