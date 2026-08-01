package com.gadzo.client.mixin;

import com.gadzo.client.core.system.FrameTimeMonitor;

import net.minecraft.client.MinecraftClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Samples one frame time per rendered frame.
 *
 * <p>Hooked at the tail of {@code render} rather than from the HUD callback so the measurement
 * covers the whole frame — world, entities, HUD and the screen on top — and keeps working
 * while a menu is open or the player is dead, which is when stutter tends to be reported.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "render(Z)V", at = @At("TAIL"))
    private void gadzo$sampleFrameTime(boolean tick, CallbackInfo ci) {
        FrameTimeMonitor.sample();
    }
}
