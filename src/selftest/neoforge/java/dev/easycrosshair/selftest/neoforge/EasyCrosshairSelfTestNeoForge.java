package dev.easycrosshair.selftest.neoforge;

import dev.easycrosshair.selftest.EasyCrosshairSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "easycrosshair_selftest", dist = Dist.CLIENT)
public final class EasyCrosshairSelfTestNeoForge {
    public EasyCrosshairSelfTestNeoForge() {
        EasyCrosshairSelfTest test = new EasyCrosshairSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
