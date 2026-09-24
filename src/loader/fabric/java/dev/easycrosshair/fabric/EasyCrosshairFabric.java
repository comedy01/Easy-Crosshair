package dev.easycrosshair.fabric;

import dev.easycrosshair.client.EasyCrosshairClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class EasyCrosshairFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EasyCrosshairClient.init(FabricLoader.getInstance().getConfigDir());
    }
}
