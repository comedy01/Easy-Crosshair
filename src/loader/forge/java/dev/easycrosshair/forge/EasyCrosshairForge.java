package dev.easycrosshair.forge;

import dev.easycrosshair.client.EasyCrosshairClient;
import dev.easycrosshair.client.gui.CrosshairSettingsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;

@Mod(EasyCrosshairClient.MOD_ID)
public final class EasyCrosshairForge {
    public EasyCrosshairForge() {
        ModLoadingContext context = ModLoadingContext.get();
        context.registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        EasyCrosshairClient.init(FMLPaths.CONFIGDIR.get());

        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> new CrosshairSettingsScreen(parent, client.options)));
    }
}
