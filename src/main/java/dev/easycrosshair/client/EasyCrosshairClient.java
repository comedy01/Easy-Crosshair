package dev.easycrosshair.client;

import dev.easycrosshair.config.CrosshairConfig;

import java.nio.file.Path;

public final class EasyCrosshairClient {
    public static final String MOD_ID = "easycrosshair";

    private static CrosshairConfig config = new CrosshairConfig();
    private static Path configPath;

    private EasyCrosshairClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(CrosshairConfig.FILE_NAME);
        config = CrosshairConfig.load(configPath);
    }

    public static CrosshairConfig config() {
        return config;
    }

    public static Path configPath() {
        return configPath;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }
}
