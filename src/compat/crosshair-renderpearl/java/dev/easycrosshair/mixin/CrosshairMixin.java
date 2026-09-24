package dev.easycrosshair.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.easycrosshair.hud.Canvas;
import dev.easycrosshair.hud.CrosshairHud;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
abstract class CrosshairMixin {
    @WrapWithCondition(
            method = "extractCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 0))
    private boolean easycrosshair$crosshair(
            GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        if (!CrosshairHud.replacesVanilla()) {
            return true;
        }
        CrosshairHud.render(new Canvas(graphics));
        return false;
    }

    @ModifyExpressionValue(
            method = "extractCrosshair",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/AttackIndicatorStatus;CROSSHAIR:Lnet/minecraft/client/AttackIndicatorStatus;"))
    private AttackIndicatorStatus easycrosshair$indicator(AttackIndicatorStatus original) {
        return CrosshairHud.hidesVanillaIndicator() ? null : original;
    }
}
