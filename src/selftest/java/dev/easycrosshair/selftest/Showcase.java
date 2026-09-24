package dev.easycrosshair.selftest;

import dev.easycrosshair.client.EasyCrosshairClient;
import dev.easycrosshair.client.gui.CrosshairSettingsScreen;
import dev.easycrosshair.config.CrosshairConfig;
import dev.easycrosshair.crosshair.CrosshairPolicy;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

final class Showcase {
    private static final double DISTANCE = 2.5D;

    private final EasyCrosshairSelfTest test;
    private int baseX;
    private int baseY;
    private int baseZ;

    Showcase(EasyCrosshairSelfTest test) {
        this.test = test;
    }

    void plan(Minecraft mc) {
        if (Boolean.getBoolean("easycrosshair.scout")) {
            scout(mc);
            return;
        }
        CrosshairConfig config = EasyCrosshairClient.config();
        int x = Integer.getInteger("easycrosshair.x", 0);
        int z = Integer.getInteger("easycrosshair.z", 0);
        float yaw = Float.parseFloat(System.getProperty("easycrosshair.yaw", "0"));
        long time = Long.getLong("easycrosshair.time", 1000L);
        test.when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> EasyCrosshairSelfTest.log("world loaded"));
        test.then(20, () -> {
            config.resetToDefaults();
            mc.options.guiScale().set(3);
            mc.options.attackIndicator().set(AttackIndicatorStatus.CROSSHAIR);
            baseX = x;
            baseZ = z;
            baseY = surface(mc, x, z);
            cmd(mc, "time set " + time);
            cmd(mc, "weather clear");
            cmd(mc, "gamerule doDaylightCycle false");
            cmd(mc, "gamerule advance_time false");
            cmd(mc, "gamerule doMobSpawning false");
            cmd(mc, "gamerule spawn_mobs false");
            cmd(mc, "gamemode survival @p");
            cmd(mc, "effect give @p resistance infinite 255 true");
            cmd(mc, "item replace entity @p weapon.mainhand with minecraft:diamond_sword");
            cmd(mc, String.format(Locale.ROOT, "tp @p %.1f %d %.1f %.1f 8", x + 0.5, baseY, z + 0.5, yaw));
        });
        test.then(80, () -> {
            cmd(mc, "tp @e[type=!player] ~ -500 ~");
            summonAhead(mc, "creeper", yaw, yaw + 180.0F);
            config.setShape(CrosshairPolicy.SHAPE_CROSS_DOT);
        });
        test.then(40, () -> aimAt(mc, 1.1));
        test.then(20, () -> test.screenshot(mc, "hostile"));
        test.then(5, () -> mc.player.resetAttackStrengthTicker());
        test.then(6, () -> test.screenshot(mc, "cooldown-bar"));
        test.then(40, () -> config.setAttackIndicator(CrosshairPolicy.INDICATOR_FADE));
        test.then(5, () -> mc.player.resetAttackStrengthTicker());
        test.then(6, () -> test.screenshot(mc, "cooldown-fade"));
        test.then(40, () -> {
            config.setAttackIndicator(CrosshairPolicy.INDICATOR_BAR);
            cmd(mc, "tp @e[type=creeper] ~ -500 ~");
            summonAhead(mc, "sheep", yaw, yaw + 90.0F);
        });
        test.then(40, () -> aimAt(mc, 0.7));
        test.then(20, () -> test.screenshot(mc, "passive"));
        test.then(5, () -> {
            cmd(mc, "tp @e[type=sheep] ~ -500 ~");
            mc.player.setXRot(4.0F);
        });
        test.then(40, () -> {
            config.setShape(CrosshairPolicy.SHAPE_CROSS);
            config.setEnabled(false);
        });
        test.then(5, () -> test.screenshot(mc, "vanilla"));
        test.then(5, () -> config.setEnabled(true));
        for (String shape : CrosshairPolicy.SHAPES) {
            test.then(10, () -> config.setShape(shape));
            test.then(5, () -> test.screenshot(mc, "shape-" + shape.toLowerCase(Locale.ROOT).replace(" + ", "-").replace(' ', '-')));
        }
        test.then(5, () -> {
            config.setShape(CrosshairPolicy.SHAPE_CIRCLE_DOT);
            config.setColorMode(CrosshairPolicy.COLOR_RAINBOW);
        });
        for (int i = 0; i < 6; i++) {
            String name = "rainbow-" + i;
            test.then(7, () -> test.screenshot(mc, name));
        }
        test.then(5, () -> {
            config.resetToDefaults();
            config.setShape(CrosshairPolicy.SHAPE_CROSS_DOT);
            config.setColor(0xFF33E0D0);
            Screens.open(mc, new CrosshairSettingsScreen(null, mc.options));
        });
        test.then(20, () -> test.screenshot(mc, "settings"));
        test.then(5, () -> Screens.open(mc, null));
        test.then(10, () -> EasyCrosshairSelfTest.log("showcase done"));
    }

    private void summonAhead(Minecraft mc, String type, float yaw, float facing) {
        double rad = Math.toRadians(yaw);
        double ax = baseX + 0.5D - Math.sin(rad) * DISTANCE;
        double az = baseZ + 0.5D + Math.cos(rad) * DISTANCE;
        int ay = surface(mc, (int) Math.floor(ax), (int) Math.floor(az));
        cmd(mc, String.format(Locale.ROOT, "summon %s %.2f %d %.2f {NoAI:1b,Rotation:[%.1ff,0f]}", type, ax, ay, az, facing));
    }

    private void scout(Minecraft mc) {
        test.when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> EasyCrosshairSelfTest.log("world loaded"));
        test.then(20, () -> {
            mc.options.guiScale().set(3);
            cmd(mc, "time set " + Long.getLong("easycrosshair.time", 1000L));
            cmd(mc, "weather clear");
            cmd(mc, "gamerule advance_time false");
        });
        int[][] spots = {{0, 0}, {160, 0}, {0, 160}, {-160, 0}, {0, -160}, {320, 320}, {-320, 320}, {320, -320}, {-320, -320}};
        for (int[] spot : spots) {
            for (int yaw = 0; yaw < 360; yaw += 120) {
                int facing = yaw;
                test.then(5, () -> cmd(mc, String.format(Locale.ROOT, "tp @p %d %d %d %d 8",
                        spot[0], surface(mc, spot[0], spot[1]), spot[1], facing)));
                test.then(facing == 0 ? 120 : 30, () -> test.screenshot(mc, "scout_" + spot[0] + "_" + spot[1] + "_" + facing));
            }
        }
    }

    private void aimAt(Minecraft mc, double height) {
        double rad = Math.toRadians(mc.player.getYRot());
        double tx = baseX + 0.5D - Math.sin(rad) * DISTANCE;
        double tz = baseZ + 0.5D + Math.cos(rad) * DISTANCE;
        double ty = surface(mc, (int) Math.floor(tx), (int) Math.floor(tz)) + height;
        double dx = tx - mc.player.getX();
        double dz = tz - mc.player.getZ();
        double dy = ty - mc.player.getEyeY();
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static int surface(Minecraft mc, int x, int z) {
        IntegratedServer server = mc.getSingleplayerServer();
        return server.submit(() -> {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            level.getChunk(x >> 4, z >> 4);
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }).join();
    }

    private static void cmd(Minecraft mc, String command) {
        EasyCrosshairSelfTest.run(mc, command);
    }
}
