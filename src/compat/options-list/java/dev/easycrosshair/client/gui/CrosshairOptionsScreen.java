package dev.easycrosshair.client.gui;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

public abstract class CrosshairOptionsScreen extends OptionsSubScreen {
    protected CrosshairOptionsScreen(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    protected void addRow(AbstractWidget left, AbstractWidget right) {
        list.addSmall(List.of(left, right));
    }
}
