package dev.easycrosshair.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.easycrosshair.hud.Canvas;
import dev.easycrosshair.hud.CrosshairHud;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
abstract class CrosshairMixin {
    @WrapWithCondition(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V",
                    ordinal = 0))
    private boolean easycrosshair$crosshair(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (!CrosshairHud.replacesVanilla()) {
            return true;
        }
        CrosshairHud.render(new Canvas(graphics));
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        return false;
    }

    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/AttackIndicatorStatus;CROSSHAIR:Lnet/minecraft/client/AttackIndicatorStatus;"))
    private AttackIndicatorStatus easycrosshair$indicator(AttackIndicatorStatus original) {
        return CrosshairHud.hidesVanillaIndicator() ? null : original;
    }
}
