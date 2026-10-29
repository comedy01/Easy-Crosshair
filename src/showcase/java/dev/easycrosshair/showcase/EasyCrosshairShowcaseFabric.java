package dev.easycrosshair.showcase;

import net.fabricmc.api.ClientModInitializer;

public final class EasyCrosshairShowcaseFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Showcase.start();
    }
}
