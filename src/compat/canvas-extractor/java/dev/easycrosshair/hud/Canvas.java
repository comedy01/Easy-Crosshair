package dev.easycrosshair.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class Canvas {
    private final GuiGraphicsExtractor graphics;

    public Canvas(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    void raise() {
        graphics.nextStratum();
    }

    void pushPixels(double guiScale) {
        float inverse = (float) (1.0D / guiScale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(inverse, inverse);
    }

    void popPixels() {
        graphics.pose().popMatrix();
    }

    void fill(int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y1, color);
    }

    void text(Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, true);
    }

    void clip(int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
    }

    void unclip() {
        graphics.disableScissor();
    }
}
