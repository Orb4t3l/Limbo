package com.orbital.limbomod;

import java.io.*;
import java.nio.file.*;

public class LimboDifficulty {

    public enum Feature {
        BLOCK_DROPS,
        MOB_DROPS,
        CRAFTING,
        TRADING,
        FISHING,
        FALLING_BLOCKS,
        EXPLOSIONS,
        CHESTS,
        ANIMAL_PRODUCTS,
        BUCKETS,
        DEATH_RESPAWN,
        UNIVERSAL_CATCHALL
    }

    private static final Feature[] TIER_ORDER = {
            Feature.BLOCK_DROPS,
            Feature.MOB_DROPS,
            Feature.CRAFTING,
            Feature.FISHING,
            Feature.ANIMAL_PRODUCTS,
            Feature.BUCKETS,
            Feature.TRADING,
            Feature.CHESTS,
            Feature.FALLING_BLOCKS,
            Feature.EXPLOSIONS,
            Feature.DEATH_RESPAWN,
            Feature.UNIVERSAL_CATCHALL
    };

    private static int level = 12;

    private static final Path CONFIG_PATH =
            Paths.get("config", "limbomod_difficulty.txt");

    public static int getLevel() { return level; }

    public static void setLevel(int newLevel) {
        level = Math.max(1, Math.min(12, newLevel));
        save();
    }

    public static boolean isEnabled(Feature feature) {
        for (int i = 0; i < level; i++) {
            if (TIER_ORDER[i] == feature) return true;
        }
        return false;
    }

    public static Feature featureUnlockedAt(int tier) {
        return TIER_ORDER[Math.max(0, Math.min(11, tier - 1))];
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String content = Files.readString(CONFIG_PATH).trim();
                level = Math.max(1, Math.min(12, Integer.parseInt(content)));
            }
        } catch (Exception ignored) {
            level = 12;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, String.valueOf(level));
        } catch (IOException ignored) {}
    }
}