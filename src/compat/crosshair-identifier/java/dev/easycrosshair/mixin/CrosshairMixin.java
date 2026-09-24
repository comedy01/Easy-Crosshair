package dev.easycrosshair.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.easycrosshair.hud.Canvas;
import dev.easycrosshair.hud.CrosshairHud;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
abstract class CrosshairMixin {
    @WrapWithCondition(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 0))
    private boolean easycrosshair$crosshair(
            GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        if (!CrosshairHud.replacesVanilla()) {
            return true;
        }
        CrosshairHud.render(new Canvas(graphics));
        return false;
    }

    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/AttackIndicatorStatus;CROSSHAIR:Lnet/minecraft/client/AttackIndicatorStatus;"))
    private AttackIndicatorStatus easycrosshair$indicator(AttackIndicatorStatus original) {
        return CrosshairHud.hidesVanillaIndicator() ? null : original;
    }
}
