package dev.easycrosshair.showcase.mixin;

import dev.easycrosshair.showcase.Showcase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.easycrosshair.hud.CrosshairHud", remap = false)
abstract class CrosshairClockMixin {
    @Redirect(method = {"render", "paintPreview"}, at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"))
    private static long ecShowcase$nanoTime() {
        return Showcase.nanos();
    }
}
