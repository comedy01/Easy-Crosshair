package dev.easycrosshair.showcase;

import com.mojang.blaze3d.platform.InputConstants;
import dev.easycrosshair.showcase.mixin.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

final class Ui {
    private Ui() {
    }

    static void click(Screen screen, double x, double y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    static void key(Screen screen, int key, int modifiers) {
        screen.keyPressed(new KeyEvent(key, 0, modifiers));
    }

    // Moves the game's idea of the mouse, in window pixels, so hovering and tooltips follow the drawn cursor.
    static void moveMouse(Minecraft mc, double x, double y) {
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
        mouse.ecShowcase$setX(x);
        mouse.ecShowcase$setY(y);
    }
}
