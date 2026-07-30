package com.gadzo.client.mixin;

import com.gadzo.client.modules.visual.Zoom;

import net.minecraft.client.Camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the zoom module's field-of-view multiplier.
 *
 * <p>Injected at RETURN and scaling the computed value, rather than overwriting it, so
 * vanilla's own FOV effects — sprinting, speed potions, fluid distortion — still compose
 * correctly underneath the zoom.
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void gadzo$applyZoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        Zoom zoom = Zoom.get();
        if (zoom == null || !zoom.isActive()) {
            return;
        }
        cir.setReturnValue((float) (cir.getReturnValue() * zoom.currentFovMultiplier()));
    }
}
