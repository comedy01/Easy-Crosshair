package dev.easycrosshair.client.gui;

import dev.easycrosshair.config.ColorSlot;
import dev.easycrosshair.hud.Canvas;
import dev.easycrosshair.hud.CrosshairHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

final class CrosshairPreview implements Renderable {
    private final Screen screen;
    private final Supplier<ColorSlot> slot;

    CrosshairPreview(Screen screen, Supplier<ColorSlot> slot) {
        this.screen = screen;
        this.slot = slot;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        CrosshairHud.paintPreview(new Canvas(graphics), screen.width, screen.height, slot.get());
    }
}
