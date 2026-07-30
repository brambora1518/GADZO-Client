package com.gadzo.client.mixin;

import com.gadzo.client.modules.performance.WeatherRender;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips the sun, moon and star pass when the weather-render module asks for it. */
@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void gadzo$skipCelestials(PoseStack poseStack, float partial, float rainLevel,
                                      float starBrightness, MoonPhase moonPhase, float skyDarken,
                                      float alpha, CallbackInfo ci) {
        WeatherRender module = WeatherRender.get();
        if (module != null && module.shouldSkipCelestials()) {
            ci.cancel();
        }
    }
}
