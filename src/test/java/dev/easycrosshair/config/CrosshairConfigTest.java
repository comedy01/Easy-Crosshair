package dev.easycrosshair.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.easycrosshair.crosshair.CrosshairPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrosshairConfigTest {
    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config").resolve(CrosshairConfig.FILE_NAME);
    }

    private void write(String json) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), json, StandardCharsets.UTF_8);
    }

    @Test
    void defaults() {
        CrosshairConfig config = new CrosshairConfig();
        assertTrue(config.enabled());
        assertEquals(CrosshairPolicy.SHAPE_CROSS, config.shape());
        assertEquals(CrosshairPolicy.COLOR_SOLID, config.colorMode());
        assertEquals(CrosshairPolicy.DEFAULT_COLOR, config.color());
        assertEquals(CrosshairPolicy.DEFAULT_LENGTH, config.length());
        assertEquals(CrosshairPolicy.DEFAULT_GAP, config.gap());
        assertEquals(CrosshairPolicy.DEFAULT_THICKNESS, config.thickness());
        assertEquals(CrosshairPolicy.DEFAULT_OUTLINE, config.outline());
        assertTrue(config.targetColors());
        assertEquals(CrosshairPolicy.INDICATOR_BAR, config.attackIndicator());
        assertFalse(config.dynamicSpread());
    }

    @Test
    void settersClamp() {
        CrosshairConfig config = new CrosshairConfig();

        config.setShape("circle + dot");
        assertEquals(CrosshairPolicy.SHAPE_CIRCLE_DOT, config.shape());
        config.setShape("triangle");
        assertEquals(CrosshairPolicy.SHAPE_CROSS, config.shape());

        config.setColorMode("RAINBOW");
        assertEquals(CrosshairPolicy.COLOR_RAINBOW, config.colorMode());
        config.setColorMode(null);
        assertEquals(CrosshairPolicy.COLOR_SOLID, config.colorMode());

        config.setLength(500);
        assertEquals(CrosshairPolicy.MAX_LENGTH, config.length());
        config.setLength(0);
        assertEquals(CrosshairPolicy.MIN_LENGTH, config.length());

        config.setGap(-4);
        assertEquals(CrosshairPolicy.MIN_GAP, config.gap());
        config.setThickness(99);
        assertEquals(CrosshairPolicy.MAX_THICKNESS, config.thickness());
        config.setOutline(-1);
        assertEquals(CrosshairPolicy.MIN_OUTLINE, config.outline());

        config.setAttackIndicator("fade");
        assertEquals(CrosshairPolicy.INDICATOR_FADE, config.attackIndicator());
        config.setAttackIndicator("sometimes");
        assertEquals(CrosshairPolicy.INDICATOR_BAR, config.attackIndicator());
    }

    @Test
    void colorSlots() {
        CrosshairConfig config = new CrosshairConfig();
        config.setColor(ColorSlot.HOSTILE, 0xFF112233);
        config.setColor(ColorSlot.PLAYER, 0x80445566);
        assertEquals(0xFF112233, config.hostileColor());
        assertEquals(0x80445566, config.color(ColorSlot.PLAYER));
        assertEquals(CrosshairPolicy.DEFAULT_COLOR, config.color(ColorSlot.CROSSHAIR));
        assertEquals(CrosshairPolicy.DEFAULT_PASSIVE_COLOR, config.color(ColorSlot.PASSIVE));
        assertEquals(ColorSlot.CROSSHAIR, ColorSlot.PLAYER.next());
    }

    @Test
    void resetToDefaults() {
        CrosshairConfig config = new CrosshairConfig();
        config.setEnabled(false);
        config.setShape(CrosshairPolicy.SHAPE_X);
        config.setColor(0x11223344);
        config.setHostileColor(0x55667788);
        config.setLength(12);
        config.setDynamicSpread(true);
        config.setAttackIndicator(CrosshairPolicy.INDICATOR_VANILLA);
        config.resetToDefaults();
        assertTrue(config.enabled());
        assertEquals(CrosshairPolicy.SHAPE_CROSS, config.shape());
        assertEquals(CrosshairPolicy.DEFAULT_COLOR, config.color());
        assertEquals(CrosshairPolicy.DEFAULT_HOSTILE_COLOR, config.hostileColor());
        assertEquals(CrosshairPolicy.DEFAULT_LENGTH, config.length());
        assertFalse(config.dynamicSpread());
        assertEquals(CrosshairPolicy.INDICATOR_BAR, config.attackIndicator());
    }

    @Test
    void saveAndLoad() throws IOException {
        CrosshairConfig config = new CrosshairConfig();
        config.setEnabled(false);
        config.setShape(CrosshairPolicy.SHAPE_SQUARE);
        config.setColorMode(CrosshairPolicy.COLOR_RAINBOW);
        config.setColor(0xCC123456);
        config.setPassiveColor(0xFF00FF00);
        config.setLength(9);
        config.setGap(0);
        config.setThickness(4);
        config.setOutline(3);
        config.setTargetColors(false);
        config.setAttackIndicator(CrosshairPolicy.INDICATOR_FADE);
        config.setDynamicSpread(true);
        config.save(file());

        CrosshairConfig reloaded = CrosshairConfig.load(file());
        assertFalse(reloaded.enabled());
        assertEquals(CrosshairPolicy.SHAPE_SQUARE, reloaded.shape());
        assertEquals(CrosshairPolicy.COLOR_RAINBOW, reloaded.colorMode());
        assertEquals(0xCC123456, reloaded.color());
        assertEquals(0xFF00FF00, reloaded.passiveColor());
        assertEquals(9, reloaded.length());
        assertEquals(0, reloaded.gap());
        assertEquals(4, reloaded.thickness());
        assertEquals(3, reloaded.outline());
        assertFalse(reloaded.targetColors());
        assertEquals(CrosshairPolicy.INDICATOR_FADE, reloaded.attackIndicator());
        assertTrue(reloaded.dynamicSpread());
    }

    @Test
    void savedKeys() throws IOException {
        new CrosshairConfig().save(file());
        String json = Files.readString(file(), StandardCharsets.UTF_8);
        for (String key : new String[] {"enabled", "shape", "colorMode", "color", "length", "gap", "thickness", "outline",
                "targetColors", "hostileColor", "passiveColor", "playerColor", "attackIndicator", "dynamicSpread"}) {
            assertTrue(json.contains("\"" + key + "\""), key + " missing from " + json);
        }
        assertFalse(Files.exists(file().resolveSibling(CrosshairConfig.FILE_NAME + ".tmp")));
    }

    @Test
    void missingFileIsCreated() {
        CrosshairConfig config = CrosshairConfig.load(file());
        assertEquals(CrosshairPolicy.SHAPE_CROSS, config.shape());
        assertTrue(Files.isRegularFile(file()));
    }

    @Test
    void outOfRangeValuesAreFixedOnLoad() throws IOException {
        write("{\"shape\": \"Hexagon\", \"length\": 900, \"gap\": -2, \"thickness\": 0, \"outline\": 12, \"attackIndicator\": \"5\"}");
        CrosshairConfig config = CrosshairConfig.load(file());
        assertEquals(CrosshairPolicy.SHAPE_CROSS, config.shape());
        assertEquals(CrosshairPolicy.MAX_LENGTH, config.length());
        assertEquals(CrosshairPolicy.MIN_GAP, config.gap());
        assertEquals(CrosshairPolicy.MIN_THICKNESS, config.thickness());
        assertEquals(CrosshairPolicy.MAX_OUTLINE, config.outline());
        assertEquals(CrosshairPolicy.INDICATOR_BAR, config.attackIndicator());
        assertEquals(CrosshairPolicy.DEFAULT_COLOR, config.color());
    }

    @Test
    void brokenFileIsKeptAside() throws IOException {
        write("{ not json");
        CrosshairConfig config = CrosshairConfig.load(file());
        assertTrue(config.enabled());
        assertTrue(Files.isRegularFile(file().resolveSibling(CrosshairConfig.FILE_NAME + ".broken")));
        assertTrue(Files.readString(file(), StandardCharsets.UTF_8).contains("\"shape\""));
    }

    @Test
    void emptyFileFallsBackToDefaults() throws IOException {
        write("");
        CrosshairConfig config = CrosshairConfig.load(file());
        assertEquals(CrosshairPolicy.DEFAULT_LENGTH, config.length());
    }
}
