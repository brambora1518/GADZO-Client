package com.gadzo.client.mixin;

import com.gadzo.client.modules.performance.WeatherRender;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips the precipitation draw when the weather-render module asks for it. */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void gadzo$skipPrecipitation(LightmapTextureManager lightmap, float tickDelta,
                                         double cameraX, double cameraY, double cameraZ,
                                         CallbackInfo ci) {
        WeatherRender module = WeatherRender.get();
        if (module != null && module.shouldSkipPrecipitation()) {
            ci.cancel();
        }
    }
}
