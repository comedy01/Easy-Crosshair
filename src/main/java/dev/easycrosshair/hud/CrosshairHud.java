package dev.easycrosshair.hud;

import dev.easycrosshair.client.EasyCrosshairClient;
import dev.easycrosshair.config.ColorSlot;
import dev.easycrosshair.config.CrosshairConfig;
import dev.easycrosshair.crosshair.CrosshairGeometry;
import dev.easycrosshair.crosshair.CrosshairPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;

public final class CrosshairHud {
    private static final int SKY = 0xFF86B3E6;
    private static final int HORIZON = 0xFFB9D5F0;
    private static final int GRASS = 0xFF5F9A38;
    private static final int DIRT = 0xFF7A5634;
    private static final int FRAME = 0xFF000000;
    private static final int LABEL = 0xFFA0A0A0;
    private static final int ROW_WIDTH = 310;
    private static final int PREVIEW_SIZE = 96;
    private static final int MIN_PREVIEW_SIZE = 40;
    private static final double PREVIEW_ZOOM = 2.0D;

    private static long frames;
    private static long lastNanos;
    private static double spread;
    private static float lastStrength = 1.0F;
    private static long readyNanos;
    private static ColorSlot lastSlot = ColorSlot.CROSSHAIR;

    private CrosshairHud() {
    }

    public static boolean replacesVanilla() {
        return EasyCrosshairClient.config().enabled();
    }

    public static boolean hidesVanillaIndicator() {
        CrosshairConfig config = EasyCrosshairClient.config();
        return config.enabled() && !CrosshairPolicy.INDICATOR_VANILLA.equals(config.attackIndicator());
    }

    public static long frames() {
        return frames;
    }

    public static double spread() {
        return spread;
    }

    public static ColorSlot lastSlot() {
        return lastSlot;
    }

    public static void render(Canvas canvas) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        CrosshairConfig config = EasyCrosshairClient.config();
        long now = System.nanoTime();
        double elapsed = lastNanos == 0L ? 0.0D : Math.min(0.25D, (now - lastNanos) / 1.0E9D);
        lastNanos = now;

        float strength = player.getAttackStrengthScale(0.0F);
        String indicator = config.attackIndicator();
        boolean custom = !CrosshairPolicy.INDICATOR_VANILLA.equals(indicator);
        if (custom && lastStrength < 1.0F && strength >= 1.0F) {
            readyNanos = now;
        }
        lastStrength = strength;
        double pulse = custom && readyNanos != 0L ? CrosshairPolicy.pulse((now - readyNanos) / 1.0E9D) : 0.0D;

        spread = CrosshairPolicy.approach(spread, config.dynamicSpread() ? targetSpread(player, config) : 0.0D, elapsed);

        lastSlot = slotFor(config, mc.crosshairPickEntity);
        int color = colorFor(config, lastSlot, now);
        if (CrosshairPolicy.INDICATOR_FADE.equals(indicator)) {
            color = CrosshairPolicy.withAlpha(color, CrosshairPolicy.fade(strength));
        }

        double scale = mc.getWindow().getGuiScale();
        int cx = mc.getWindow().getWidth() / 2;
        int cy = mc.getWindow().getHeight() / 2;
        double gapUnits = Math.max(0.0D, config.gap() + spread) + pulse * (config.gap() + config.length());

        canvas.pushPixels(scale);
        int reach = draw(canvas, cx, cy, scale, config, gapUnits, color);
        if (CrosshairPolicy.INDICATOR_BAR.equals(indicator) && strength < 1.0F) {
            drawBar(canvas, cx, cy + reach, scale, config, strength, colorFor(config, ColorSlot.CROSSHAIR, now));
        }
        canvas.popPixels();
        frames++;
    }

    public static void paintPreview(Canvas canvas, int screenWidth, int screenHeight, ColorSlot slot) {
        int margin = (screenWidth - ROW_WIDTH) / 2;
        int size = Math.min(PREVIEW_SIZE, margin - 24);
        if (size < MIN_PREVIEW_SIZE) {
            return;
        }
        int x = (margin - size) / 2;
        int y = (screenHeight - size) / 2;
        Minecraft mc = Minecraft.getInstance();
        CrosshairConfig config = EasyCrosshairClient.config();
        double scale = mc.getWindow().getGuiScale();
        int ground = y + size * 2 / 3;

        canvas.raise();
        canvas.text(mc.font, Component.translatable("easycrosshair.options.preview"), x, y - 11, LABEL);
        canvas.fill(x - 1, y - 1, x + size + 1, y + size + 1, FRAME);
        canvas.fill(x, y, x + size, y + size / 3, SKY);
        canvas.fill(x, y + size / 3, x + size, ground, HORIZON);
        canvas.fill(x, ground, x + size, ground + 3, GRASS);
        canvas.fill(x, ground + 3, x + size, y + size, DIRT);

        canvas.clip(x, y, x + size, y + size);
        canvas.pushPixels(scale);
        int cx = (int) Math.round((x + size / 2.0D) * scale);
        int cy = (int) Math.round((y + size / 2.0D) * scale);
        draw(canvas, cx, cy, scale * PREVIEW_ZOOM, config, config.gap(), colorFor(config, slot, System.nanoTime()));
        canvas.popPixels();
        canvas.unclip();
    }

    private static int draw(Canvas canvas, int cx, int cy, double scale, CrosshairConfig config, double gapUnits, int color) {
        int length = CrosshairPolicy.pixels(config.length(), scale);
        double gap = gapUnits * Math.max(1.0D, scale) / 2.0D;
        int thickness = CrosshairPolicy.pixels(config.thickness(), scale);
        int outline = CrosshairPolicy.pixels(config.outline(), scale);
        String shape = config.shape();

        if (outline > 0) {
            int shade = CrosshairPolicy.outlineColor(color);
            for (int[] r : CrosshairGeometry.build(shape, length, gap, thickness, outline)) {
                canvas.fill(cx + r[0], cy + r[1], cx + r[2], cy + r[3], shade);
            }
        }
        for (int[] r : CrosshairGeometry.build(shape, length, gap, thickness, 0)) {
            canvas.fill(cx + r[0], cy + r[1], cx + r[2], cy + r[3], color);
        }
        return CrosshairGeometry.reach(shape, length, gap, thickness) + outline;
    }

    private static void drawBar(Canvas canvas, int cx, int top, double scale, CrosshairConfig config, float strength, int color) {
        int half = Math.max(CrosshairPolicy.pixels(6, scale), CrosshairPolicy.pixels(config.length() + config.gap(), scale));
        int height = Math.max(CrosshairPolicy.pixels(2, scale), CrosshairPolicy.pixels(config.thickness(), scale));
        int outline = Math.max(1, CrosshairPolicy.pixels(config.outline(), scale));
        int y0 = top + CrosshairPolicy.pixels(4, scale);
        int x0 = cx - half;
        int x1 = cx + half;
        int filled = x0 + Math.round((x1 - x0) * Math.max(0.0F, Math.min(1.0F, strength)));

        canvas.fill(x0 - outline, y0 - outline, x1 + outline, y0 + height + outline, CrosshairPolicy.outlineColor(CrosshairPolicy.withAlpha(color, 0.6F)));
        canvas.fill(filled, y0, x1, y0 + height, CrosshairPolicy.withAlpha(color, 0.3F));
        canvas.fill(x0, y0, filled, y0 + height, color);
    }

    private static double targetSpread(LocalPlayer player, CrosshairConfig config) {
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof ProjectileWeaponItem) {
            return -config.gap() * 0.8D * CrosshairPolicy.drawFocus(player.getTicksUsingItem());
        }
        Vec3 motion = player.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        return CrosshairPolicy.spread(horizontal, player.onGround(), player.isSprinting());
    }

    private static ColorSlot slotFor(CrosshairConfig config, Entity target) {
        if (!config.targetColors() || target == null || !target.isAlive()) {
            return ColorSlot.CROSSHAIR;
        }
        if (target instanceof Player) {
            return ColorSlot.PLAYER;
        }
        if (target instanceof Enemy) {
            return ColorSlot.HOSTILE;
        }
        if (target instanceof LivingEntity) {
            return ColorSlot.PASSIVE;
        }
        return ColorSlot.CROSSHAIR;
    }

    private static int colorFor(CrosshairConfig config, ColorSlot slot, long now) {
        if (slot == ColorSlot.CROSSHAIR && CrosshairPolicy.isRainbow(config.colorMode())) {
            return CrosshairPolicy.rainbow(now / 1.0E9D, config.color() >>> 24);
        }
        return config.color(slot);
    }
}
