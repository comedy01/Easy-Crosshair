package dev.easycrosshair.selftest.fabric;

import dev.easycrosshair.selftest.EasyCrosshairSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class EasyCrosshairSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EasyCrosshairSelfTest test = new EasyCrosshairSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
