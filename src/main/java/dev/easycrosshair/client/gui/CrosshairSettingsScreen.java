package dev.easycrosshair.client.gui;

import dev.easycrosshair.client.EasyCrosshairClient;
import dev.easycrosshair.config.ColorSlot;
import dev.easycrosshair.config.CrosshairConfig;
import dev.easycrosshair.crosshair.CrosshairPolicy;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class CrosshairSettingsScreen extends CrosshairOptionsScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;
    private static final int[] SHIFTS = {16, 8, 0, 24};
    private static final String[] CHANNELS = {"red", "green", "blue", "alpha"};

    private ColorSlot slot = ColorSlot.CROSSHAIR;
    private StepSlider[] channels;

    public CrosshairSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.translatable("easycrosshair.options.title"));
    }

    public ColorSlot slot() {
        return slot;
    }

    @Override
    protected void addOptions() {
        CrosshairConfig config = EasyCrosshairClient.config();
        channels = new StepSlider[SHIFTS.length];
        for (int i = 0; i < SHIFTS.length; i++) {
            int shift = SHIFTS[i];
            channels[i] = intSlider(
                    "easycrosshair.options." + CHANNELS[i],
                    i == 3 ? "easycrosshair.options.alpha.tooltip" : "easycrosshair.options.color.tooltip",
                    0,
                    255,
                    (config.color(slot) >>> shift) & 0xFF,
                    value -> setChannel(config, shift, value));
        }

        addRow(
                toggle("easycrosshair.options.enabled", config::enabled, config::setEnabled),
                cycle("easycrosshair.options.shape", () -> shapeName(config.shape()),
                        () -> config.setShape(CrosshairPolicy.nextShape(config.shape()))));

        addRow(
                slotButton(config),
                cycle("easycrosshair.options.color_mode", () -> colorModeName(config.colorMode()),
                        () -> config.setColorMode(CrosshairPolicy.isRainbow(config.colorMode())
                                ? CrosshairPolicy.COLOR_SOLID
                                : CrosshairPolicy.COLOR_RAINBOW)));

        addRow(channels[0], channels[1]);
        addRow(channels[2], channels[3]);

        addRow(
                intSlider("easycrosshair.options.length", "easycrosshair.options.length.tooltip",
                        CrosshairPolicy.MIN_LENGTH, CrosshairPolicy.MAX_LENGTH, config.length(), config::setLength),
                intSlider("easycrosshair.options.gap", "easycrosshair.options.gap.tooltip",
                        CrosshairPolicy.MIN_GAP, CrosshairPolicy.MAX_GAP, config.gap(), config::setGap));

        addRow(
                intSlider("easycrosshair.options.thickness", "easycrosshair.options.thickness.tooltip",
                        CrosshairPolicy.MIN_THICKNESS, CrosshairPolicy.MAX_THICKNESS, config.thickness(), config::setThickness),
                intSlider("easycrosshair.options.outline", "easycrosshair.options.outline.tooltip",
                        CrosshairPolicy.MIN_OUTLINE, CrosshairPolicy.MAX_OUTLINE, config.outline(), config::setOutline));

        addRow(
                toggle("easycrosshair.options.target_colors", config::targetColors, config::setTargetColors),
                cycle("easycrosshair.options.attack_indicator", () -> indicatorName(config.attackIndicator()),
                        () -> config.setAttackIndicator(CrosshairPolicy.nextIndicator(config.attackIndicator()))));

        addRow(
                toggle("easycrosshair.options.dynamic_spread", config::dynamicSpread, config::setDynamicSpread),
                resetButton(config));
    }

    @Override
    protected void init() {
        super.init();
        addRenderableOnly(new CrosshairPreview(this, this::slot));
    }

    @Override
    public void removed() {
        super.removed();
        EasyCrosshairClient.saveConfig();
    }

    private AbstractWidget slotButton(CrosshairConfig config) {
        return Button.builder(slotLabel(), button -> {
                    slot = slot.next();
                    button.setMessage(slotLabel());
                    for (int i = 0; i < SHIFTS.length; i++) {
                        channels[i].setCurrent((config.color(slot) >>> SHIFTS[i]) & 0xFF);
                    }
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable("easycrosshair.options.editing.tooltip")))
                .build();
    }

    private Component slotLabel() {
        return Component.translatable("easycrosshair.options.editing",
                Component.translatable("easycrosshair.options.slot." + slot.key()));
    }

    private AbstractWidget resetButton(CrosshairConfig config) {
        return Button.builder(Component.translatable("easycrosshair.options.reset"), button -> {
                    config.resetToDefaults();
                    ScreenOpener.open(minecraft, new CrosshairSettingsScreen(lastScreen, options));
                })
                .width(WIDTH)
                .build();
    }

    private void setChannel(CrosshairConfig config, int shift, int value) {
        int mask = ~(0xFF << shift);
        config.setColor(slot, (config.color(slot) & mask) | ((value & 0xFF) << shift));
    }

    private static AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return Button.builder(toggleLabel(key, getter.getAsBoolean()), button -> {
                    setter.accept(!getter.getAsBoolean());
                    button.setMessage(toggleLabel(key, getter.getAsBoolean()));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                .build();
    }

    private static Component toggleLabel(String key, boolean on) {
        Component state = Component.translatable(on ? "options.on" : "options.off");
        return Component.translatable("options.generic_value", Component.translatable(key), state);
    }

    private static AbstractWidget cycle(String key, Supplier<Component> value, Runnable advance) {
        return Button.builder(Component.translatable(key, value.get()), button -> {
                    advance.run();
                    button.setMessage(Component.translatable(key, value.get()));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                .build();
    }

    private static Component shapeName(String shape) {
        return Component.translatable("easycrosshair.shape." + key(shape));
    }

    private static Component colorModeName(String mode) {
        return Component.translatable("easycrosshair.color_mode." + key(mode));
    }

    private static Component indicatorName(String indicator) {
        return Component.translatable("easycrosshair.indicator." + key(indicator));
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT).replace(" + ", "_").replace('-', '_');
    }

    private static StepSlider intSlider(String captionKey, String tooltipKey, int min, int max, int initial, IntConsumer onChange) {
        return new StepSlider(
                captionKey,
                tooltipKey,
                min,
                max,
                1,
                initial,
                value -> Integer.toString((int) value),
                value -> onChange.accept((int) value));
    }

    private static final class StepSlider extends AbstractSliderButton {
        private final String captionKey;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleFunction<String> format;
        private final DoubleConsumer onChange;

        StepSlider(
                String captionKey,
                String tooltipKey,
                double min,
                double max,
                double step,
                double initial,
                DoubleFunction<String> format,
                DoubleConsumer onChange) {

            super(0, 0, WIDTH, HEIGHT, Component.empty(), 0.0);
            this.captionKey = captionKey;
            this.min = min;
            this.max = max;
            this.step = step;
            this.format = format;
            this.onChange = onChange;
            this.value = (snap(initial) - min) / (max - min);
            setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            updateMessage();
        }

        void setCurrent(double raw) {
            value = (snap(raw) - min) / (max - min);
            updateMessage();
        }

        private double snap(double raw) {
            double clamped = Math.max(min, Math.min(max, raw));
            double snapped = min + Math.round((clamped - min) / step) * step;
            return Math.max(min, Math.min(max, snapped));
        }

        private double current() {
            return snap(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            Component shown = Component.literal(format.apply(current()));
            setMessage(Component.translatable("options.generic_value", Component.translatable(captionKey), shown));
        }

        @Override
        protected void applyValue() {
            double snapped = current();
            value = (snapped - min) / (max - min);
            onChange.accept(snapped);
        }
    }
}
