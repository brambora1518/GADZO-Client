package com.gadzo.client.mixin;

import com.gadzo.client.modules.performance.WeatherRender;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips the precipitation draw when the weather-render module asks for it. */
@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void gadzo$skipPrecipitation(Vec3 cameraPosition, WeatherRenderState state, CallbackInfo ci) {
        WeatherRender module = WeatherRender.get();
        if (module != null && module.shouldSkipPrecipitation()) {
            ci.cancel();
        }
    }
}
