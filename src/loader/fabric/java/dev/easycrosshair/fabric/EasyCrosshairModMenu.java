package dev.easycrosshair.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.easycrosshair.client.gui.CrosshairSettingsScreen;
import net.minecraft.client.Minecraft;

public final class EasyCrosshairModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new CrosshairSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
