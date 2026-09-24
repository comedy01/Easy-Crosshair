package dev.easycrosshair.neoforge;

import dev.easycrosshair.client.EasyCrosshairClient;
import dev.easycrosshair.client.gui.CrosshairSettingsScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = EasyCrosshairClient.MOD_ID, dist = Dist.CLIENT)
public final class EasyCrosshairNeoForge {
    public EasyCrosshairNeoForge(IEventBus modBus, ModContainer container) {
        EasyCrosshairClient.init(FMLPaths.CONFIGDIR.get());

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new CrosshairSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
