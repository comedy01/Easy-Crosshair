package dev.easycrosshair.crosshair;

import java.util.List;

public final class CrosshairPolicy {
    public static final String SHAPE_CROSS = "Cross";
    public static final String SHAPE_CROSS_DOT = "Cross + Dot";
    public static final String SHAPE_DOT = "Dot";
    public static final String SHAPE_CIRCLE = "Circle";
    public static final String SHAPE_CIRCLE_DOT = "Circle + Dot";
    public static final String SHAPE_T = "T-Shape";
    public static final String SHAPE_X = "X";
    public static final String SHAPE_SQUARE = "Square";
    public static final List<String> SHAPES = List.of(
            SHAPE_CROSS, SHAPE_CROSS_DOT, SHAPE_DOT, SHAPE_CIRCLE, SHAPE_CIRCLE_DOT, SHAPE_T, SHAPE_X, SHAPE_SQUARE);

    public static final String COLOR_SOLID = "Solid";
    public static final String COLOR_RAINBOW = "Rainbow";

    public static final String INDICATOR_BAR = "Bar";
    public static final String INDICATOR_FADE = "Fade";
    public static final String INDICATOR_VANILLA = "Vanilla";
    public static final List<String> INDICATORS = List.of(INDICATOR_BAR, INDICATOR_FADE, INDICATOR_VANILLA);

    public static final int DEFAULT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_HOSTILE_COLOR = 0xFFFF4B4B;
    public static final int DEFAULT_PASSIVE_COLOR = 0xFF5BE35B;
    public static final int DEFAULT_PLAYER_COLOR = 0xFF4FA8FF;
    public static final int OUTLINE_RGB = 0x000000;

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 20;
    public static final int DEFAULT_LENGTH = 6;

    public static final int MIN_GAP = 0;
    public static final int MAX_GAP = 20;
    public static final int DEFAULT_GAP = 3;

    public static final int MIN_THICKNESS = 1;
    public static final int MAX_THICKNESS = 10;
    public static final int DEFAULT_THICKNESS = 2;

    public static final int MIN_OUTLINE = 0;
    public static final int MAX_OUTLINE = 4;
    public static final int DEFAULT_OUTLINE = 1;

    static final double RAINBOW_SECONDS = 4.0D;
    static final double MAX_SPREAD = 6.0D;
    static final double PULSE_SECONDS = 0.25D;
    static final double PULSE_GROWTH = 0.35D;
    static final float FADE_FLOOR = 0.25F;

    private CrosshairPolicy() {
    }

    public static String normalizeShape(String shape) {
        return pick(SHAPES, shape, SHAPE_CROSS);
    }

    public static String nextShape(String shape) {
        return next(SHAPES, normalizeShape(shape));
    }

    public static String normalizeColorMode(String mode) {
        return COLOR_RAINBOW.equalsIgnoreCase(mode == null ? "" : mode.trim()) ? COLOR_RAINBOW : COLOR_SOLID;
    }

    public static boolean isRainbow(String mode) {
        return COLOR_RAINBOW.equals(normalizeColorMode(mode));
    }

    public static String normalizeIndicator(String indicator) {
        return pick(INDICATORS, indicator, INDICATOR_BAR);
    }

    public static String nextIndicator(String indicator) {
        return next(INDICATORS, normalizeIndicator(indicator));
    }

    public static int clampLength(int value) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, value));
    }

    public static int clampGap(int value) {
        return Math.max(MIN_GAP, Math.min(MAX_GAP, value));
    }

    public static int clampThickness(int value) {
        return Math.max(MIN_THICKNESS, Math.min(MAX_THICKNESS, value));
    }

    public static int clampOutline(int value) {
        return Math.max(MIN_OUTLINE, Math.min(MAX_OUTLINE, value));
    }

    public static int pixels(double units, double guiScale) {
        if (!(units > 0.0D)) {
            return 0;
        }
        return Math.max(1, (int) Math.round(units * Math.max(1.0D, guiScale) / 2.0D));
    }

    public static int rainbow(double seconds, int alpha) {
        double hue = seconds / RAINBOW_SECONDS % 1.0D;
        double h = (hue < 0.0D ? hue + 1.0D : hue) * 6.0D;
        int sector = (int) h;
        double f = h - sector;
        int rising = channel(f);
        int falling = channel(1.0D - f);
        int rgb = switch (sector) {
            case 0 -> pack(255, rising, 0);
            case 1 -> pack(falling, 255, 0);
            case 2 -> pack(0, 255, rising);
            case 3 -> pack(0, falling, 255);
            case 4 -> pack(rising, 0, 255);
            default -> pack(255, 0, falling);
        };
        return ((alpha & 0xFF) << 24) | rgb;
    }

    public static int withAlpha(int color, float factor) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, factor)));
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    public static int outlineColor(int color) {
        return (color & 0xFF000000) | OUTLINE_RGB;
    }

    public static double spread(double horizontalSpeed, boolean onGround, boolean sprinting) {
        double target = horizontalSpeed * 20.0D;
        if (sprinting) {
            target += 1.0D;
        }
        if (!onGround) {
            target += 2.0D;
        }
        return Math.max(0.0D, Math.min(MAX_SPREAD, target));
    }

    public static double drawFocus(int ticksUsing) {
        return Math.max(0.0D, Math.min(1.0D, ticksUsing / 20.0D));
    }

    public static double approach(double current, double target, double seconds) {
        double blend = 1.0D - Math.exp(-12.0D * Math.max(0.0D, seconds));
        return current + (target - current) * blend;
    }

    public static double pulse(double secondsSinceReady) {
        if (secondsSinceReady < 0.0D || secondsSinceReady >= PULSE_SECONDS) {
            return 0.0D;
        }
        double t = secondsSinceReady / PULSE_SECONDS;
        return PULSE_GROWTH * Math.sin(Math.PI * t);
    }

    public static float fade(float attackStrength) {
        return FADE_FLOOR + (1.0F - FADE_FLOOR) * Math.max(0.0F, Math.min(1.0F, attackStrength));
    }

    private static String pick(List<String> values, String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        for (String candidate : values) {
            if (candidate.equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        return fallback;
    }

    private static String next(List<String> values, String value) {
        return values.get((values.indexOf(value) + 1) % values.size());
    }

    private static int channel(double fraction) {
        return (int) Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * 255.0D);
    }

    private static int pack(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
