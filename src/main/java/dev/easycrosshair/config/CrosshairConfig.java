package dev.easycrosshair.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import dev.easycrosshair.crosshair.CrosshairPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class CrosshairConfig {
    public static final String FILE_NAME = "easycrosshair.json";
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_TARGET_COLORS = true;
    public static final boolean DEFAULT_DYNAMIC_SPREAD = false;

    private static final Logger LOGGER = LoggerFactory.getLogger("easycrosshair");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("enabled")
    private boolean enabled = DEFAULT_ENABLED;

    @SerializedName("shape")
    private String shape = CrosshairPolicy.SHAPE_CROSS;

    @SerializedName("colorMode")
    private String colorMode = CrosshairPolicy.COLOR_SOLID;

    @SerializedName("color")
    private int color = CrosshairPolicy.DEFAULT_COLOR;

    @SerializedName("length")
    private int length = CrosshairPolicy.DEFAULT_LENGTH;

    @SerializedName("gap")
    private int gap = CrosshairPolicy.DEFAULT_GAP;

    @SerializedName("thickness")
    private int thickness = CrosshairPolicy.DEFAULT_THICKNESS;

    @SerializedName("outline")
    private int outline = CrosshairPolicy.DEFAULT_OUTLINE;

    @SerializedName("targetColors")
    private boolean targetColors = DEFAULT_TARGET_COLORS;

    @SerializedName("hostileColor")
    private int hostileColor = CrosshairPolicy.DEFAULT_HOSTILE_COLOR;

    @SerializedName("passiveColor")
    private int passiveColor = CrosshairPolicy.DEFAULT_PASSIVE_COLOR;

    @SerializedName("playerColor")
    private int playerColor = CrosshairPolicy.DEFAULT_PLAYER_COLOR;

    @SerializedName("attackIndicator")
    private String attackIndicator = CrosshairPolicy.INDICATOR_BAR;

    @SerializedName("dynamicSpread")
    private boolean dynamicSpread = DEFAULT_DYNAMIC_SPREAD;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String shape() {
        return shape;
    }

    public void setShape(String value) {
        shape = CrosshairPolicy.normalizeShape(value);
    }

    public String colorMode() {
        return colorMode;
    }

    public void setColorMode(String value) {
        colorMode = CrosshairPolicy.normalizeColorMode(value);
    }

    public int color() {
        return color;
    }

    public void setColor(int value) {
        color = value;
    }

    public int length() {
        return length;
    }

    public void setLength(int value) {
        length = CrosshairPolicy.clampLength(value);
    }

    public int gap() {
        return gap;
    }

    public void setGap(int value) {
        gap = CrosshairPolicy.clampGap(value);
    }

    public int thickness() {
        return thickness;
    }

    public void setThickness(int value) {
        thickness = CrosshairPolicy.clampThickness(value);
    }

    public int outline() {
        return outline;
    }

    public void setOutline(int value) {
        outline = CrosshairPolicy.clampOutline(value);
    }

    public boolean targetColors() {
        return targetColors;
    }

    public void setTargetColors(boolean value) {
        targetColors = value;
    }

    public int hostileColor() {
        return hostileColor;
    }

    public void setHostileColor(int value) {
        hostileColor = value;
    }

    public int passiveColor() {
        return passiveColor;
    }

    public void setPassiveColor(int value) {
        passiveColor = value;
    }

    public int playerColor() {
        return playerColor;
    }

    public void setPlayerColor(int value) {
        playerColor = value;
    }

    public int color(ColorSlot slot) {
        return switch (slot) {
            case HOSTILE -> hostileColor;
            case PASSIVE -> passiveColor;
            case PLAYER -> playerColor;
            default -> color;
        };
    }

    public void setColor(ColorSlot slot, int value) {
        switch (slot) {
            case HOSTILE -> hostileColor = value;
            case PASSIVE -> passiveColor = value;
            case PLAYER -> playerColor = value;
            default -> color = value;
        }
    }

    public String attackIndicator() {
        return attackIndicator;
    }

    public void setAttackIndicator(String value) {
        attackIndicator = CrosshairPolicy.normalizeIndicator(value);
    }

    public boolean dynamicSpread() {
        return dynamicSpread;
    }

    public void setDynamicSpread(boolean value) {
        dynamicSpread = value;
    }

    public void resetToDefaults() {
        enabled = DEFAULT_ENABLED;
        shape = CrosshairPolicy.SHAPE_CROSS;
        colorMode = CrosshairPolicy.COLOR_SOLID;
        color = CrosshairPolicy.DEFAULT_COLOR;
        length = CrosshairPolicy.DEFAULT_LENGTH;
        gap = CrosshairPolicy.DEFAULT_GAP;
        thickness = CrosshairPolicy.DEFAULT_THICKNESS;
        outline = CrosshairPolicy.DEFAULT_OUTLINE;
        targetColors = DEFAULT_TARGET_COLORS;
        hostileColor = CrosshairPolicy.DEFAULT_HOSTILE_COLOR;
        passiveColor = CrosshairPolicy.DEFAULT_PASSIVE_COLOR;
        playerColor = CrosshairPolicy.DEFAULT_PLAYER_COLOR;
        attackIndicator = CrosshairPolicy.INDICATOR_BAR;
        dynamicSpread = DEFAULT_DYNAMIC_SPREAD;
    }

    private void sanitize() {
        setShape(shape);
        setColorMode(colorMode);
        setLength(length);
        setGap(gap);
        setThickness(thickness);
        setOutline(outline);
        setAttackIndicator(attackIndicator);
    }

    public static CrosshairConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            CrosshairConfig fresh = new CrosshairConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CrosshairConfig loaded = GSON.fromJson(reader, CrosshairConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            loaded.sanitize();
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            CrosshairConfig fresh = new CrosshairConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(
                    file,
                    file.resolveSibling(file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
