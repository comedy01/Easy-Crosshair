package dev.easycrosshair.selftest.forge;

import dev.easycrosshair.selftest.EasyCrosshairSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("easycrosshair_selftest")
public final class EasyCrosshairSelfTestForge {
    public EasyCrosshairSelfTestForge() {
        EasyCrosshairSelfTest test = new EasyCrosshairSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
