package dev.easycrosshair.selftest;

import dev.easycrosshair.client.EasyCrosshairClient;
import dev.easycrosshair.client.gui.CrosshairSettingsScreen;
import dev.easycrosshair.config.ColorSlot;
import dev.easycrosshair.config.CrosshairConfig;
import dev.easycrosshair.crosshair.CrosshairPolicy;
import dev.easycrosshair.hud.CrosshairHud;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class EasyCrosshairSelfTest {
    private static final String WORLD = "easycrosshair-selftest";
    private static final int TIMEOUT = 2400;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private long mark;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                rememberShots(mc);
                if (Boolean.getBoolean("easycrosshair.showcase")) {
                    new Showcase(this).plan(mc);
                } else {
                    plan(mc);
                }
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, !Boolean.getBoolean("easycrosshair.showcase"), Long.getLong("easycrosshair.seed", 0L));
            }
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        nameShots(mc);
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        CrosshairConfig config = EasyCrosshairClient.config();
        config.resetToDefaults();
        EasyCrosshairClient.saveConfig();
        mc.stop();
    }

    private void plan(Minecraft mc) {
        CrosshairConfig config = EasyCrosshairClient.config();
        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded"));
        then(40, () -> {
            config.resetToDefaults();
            mc.options.guiScale().set(3);
            mc.options.attackIndicator().set(AttackIndicatorStatus.CROSSHAIR);
            run(mc, "time set 6000");
            run(mc, "tp @p 0 -60 0 0 25");
        });
        then(20, () -> {
            check(CrosshairHud.frames() > 0, "the custom crosshair was never drawn");
            check(CrosshairHud.lastSlot() == ColorSlot.CROSSHAIR, "target color used while aiming at nothing");
            screenshot(mc, "default");
            config.setEnabled(false);
            mark = CrosshairHud.frames();
        });
        then(10, () -> {
            check(CrosshairHud.frames() == mark, "the custom crosshair was drawn while switched off");
            screenshot(mc, "vanilla");
            config.setEnabled(true);
            run(mc, "summon creeper 0 -60 3 {NoAI:1b,Rotation:[180f,0f]}");
        });
        then(20, () -> {
            check(CrosshairHud.frames() > mark, "the crosshair did not come back after switching it on");
            check(CrosshairHud.lastSlot() == ColorSlot.HOSTILE, "creeper not colored as hostile: " + CrosshairHud.lastSlot());
            screenshot(mc, "hostile");
            run(mc, "kill @e[type=creeper]");
            run(mc, "summon pig 0 -60 3 {NoAI:1b,Rotation:[180f,0f]}");
        });
        then(20, () -> {
            check(CrosshairHud.lastSlot() == ColorSlot.PASSIVE, "pig not colored as passive: " + CrosshairHud.lastSlot());
            screenshot(mc, "passive");
            config.setTargetColors(false);
        });
        then(5, () -> {
            check(CrosshairHud.lastSlot() == ColorSlot.CROSSHAIR, "target colors still used after switching them off");
            config.setTargetColors(true);
            run(mc, "kill @e[type=pig]");
        });
        then(10, () -> {
            check(CrosshairHud.hidesVanillaIndicator(), "vanilla attack indicator not replaced by the bar");
            mc.player.resetAttackStrengthTicker();
        });
        then(2, () -> {
            check(mc.player.getAttackStrengthScale(0.0F) < 1.0F, "attack strength did not reset");
            screenshot(mc, "cooldown-bar");
            config.setAttackIndicator(CrosshairPolicy.INDICATOR_FADE);
        });
        then(30, () -> mc.player.resetAttackStrengthTicker());
        then(2, () -> {
            screenshot(mc, "cooldown-fade");
            config.setAttackIndicator(CrosshairPolicy.INDICATOR_VANILLA);
            check(!CrosshairHud.hidesVanillaIndicator(), "vanilla attack indicator hidden in Vanilla mode");
        });
        then(30, () -> mc.player.resetAttackStrengthTicker());
        then(2, () -> {
            screenshot(mc, "cooldown-vanilla");
            config.setAttackIndicator(CrosshairPolicy.INDICATOR_BAR);
            config.setDynamicSpread(true);
            mc.player.getAbilities().flying = true;
            run(mc, "tp @p 0 -55 0 0 25");
        });
        then(30, () -> {
            check(CrosshairHud.spread() > 1.0D, "dynamic spread did not open up in the air: " + CrosshairHud.spread());
            screenshot(mc, "spread");
            config.setDynamicSpread(false);
            mc.player.getAbilities().flying = false;
            run(mc, "tp @p 0 -60 0 0 25");
        });
        for (String shape : CrosshairPolicy.SHAPES) {
            then(10, () -> config.setShape(shape));
            then(5, () -> screenshot(mc, "shape-" + shape.toLowerCase(Locale.ROOT).replace(" + ", "-").replace(' ', '-')));
        }
        then(5, () -> {
            config.setShape(CrosshairPolicy.SHAPE_CROSS);
            config.setColorMode(CrosshairPolicy.COLOR_RAINBOW);
        });
        then(10, () -> {
            screenshot(mc, "rainbow");
            config.setColorMode(CrosshairPolicy.COLOR_SOLID);
        });
        planSettings(mc, config);
    }

    private void planSettings(Minecraft mc, CrosshairConfig config) {
        then(5, () -> Screens.open(mc, new CrosshairSettingsScreen(null, mc.options)));
        then(10, () -> {
            screenshot(mc, "settings");
            click(mc, find(Screens.current(mc), "easycrosshair.options.shape"));
        });
        then(3, () -> {
            check(CrosshairPolicy.SHAPE_CROSS_DOT.equals(config.shape()), "shape button did not advance: " + config.shape());
            click(mc, find(Screens.current(mc), "easycrosshair.options.editing"));
        });
        then(3, () -> {
            CrosshairSettingsScreen screen = (CrosshairSettingsScreen) Screens.current(mc);
            check(screen.slot() == ColorSlot.HOSTILE, "color button did not switch to Hostile: " + screen.slot());
            String green = slider(screen, "Green").getMessage().getString();
            check(green.endsWith(": 75"), "green slider not showing the hostile color: " + green);
            clickRightEdge(mc, slider(screen, "Green"));
        });
        then(3, () -> {
            check(((config.hostileColor() >>> 8) & 0xFF) == 255, "green slider did not change the hostile color");
            check(config.color() == CrosshairPolicy.DEFAULT_COLOR, "editing hostile changed the crosshair color");
            screenshot(mc, "settings-hostile");
            click(mc, find(Screens.current(mc), "easycrosshair.options.reset"));
        });
        then(5, () -> {
            Screen screen = Screens.current(mc);
            check(config.hostileColor() == CrosshairPolicy.DEFAULT_HOSTILE_COLOR, "reset did not restore the hostile color");
            check(CrosshairPolicy.SHAPE_CROSS.equals(config.shape()), "reset did not restore the shape");
            check(count(screen, "Length") == 1, "reset left " + count(screen, "Length") + " length sliders");
            check(((CrosshairSettingsScreen) screen).slot() == ColorSlot.CROSSHAIR, "reset kept the color slot");
            clickRightEdge(mc, slider(screen, "Length"));
        });
        then(3, () -> {
            check(config.length() == CrosshairPolicy.MAX_LENGTH, "length slider after reset did not change the config");
            Screens.open(mc, null);
        });
        then(5, () -> {
            String saved = read(EasyCrosshairClient.configPath());
            check(saved.contains("\"length\": 20"), "settings were not saved on close: " + saved);
        });
        then(10, () -> log("settings checks done"));
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        Screenshot.grab(mc.gameDirectory, Screens.renderTarget(mc), message -> {
        });
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "ec-%02d-%s.png", i, shots.get(i)));
            if (fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void click(Minecraft mc, AbstractWidget widget) {
        Ui.click(Screens.current(mc), widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static void clickRightEdge(Minecraft mc, AbstractWidget widget) {
        Ui.click(Screens.current(mc), widget.getX() + widget.getWidth() - 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static AbstractWidget find(GuiEventListener node, String key) {
        if (node instanceof AbstractWidget widget
                && widget.getMessage().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key)) {
            return widget;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Screen) {
            throw new AssertionError("no widget " + key + " on " + node);
        }
        return null;
    }

    private static AbstractSliderButton slider(GuiEventListener node, String caption) {
        AbstractSliderButton found = findSlider(node, caption);
        check(found != null, "no slider " + caption);
        return found;
    }

    private static AbstractSliderButton findSlider(GuiEventListener node, String caption) {
        if (node instanceof AbstractSliderButton slider && slider.getMessage().getString().startsWith(caption)) {
            return slider;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractSliderButton found = findSlider(child, caption);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int count(GuiEventListener node, String caption) {
        int total = node instanceof AbstractSliderButton slider && slider.getMessage().getString().startsWith(caption) ? 1 : 0;
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                total += count(child, caption);
            }
        }
        return total;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[easycrosshair-selftest] " + message);
    }
}
