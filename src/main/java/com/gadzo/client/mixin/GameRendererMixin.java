package com.gadzo.client.mixin;

import com.gadzo.client.modules.visual.Zoom;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the zoom module's field-of-view multiplier.
 *
 * <p>Injected at RETURN and scaling the computed value rather than overwriting it, so
 * vanilla's own FOV effects still compose underneath the zoom.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void gadzo$applyZoom(Camera camera, float tickDelta, boolean changingFov,
                                 CallbackInfoReturnable<Double> cir) {
        Zoom zoom = Zoom.get();
        if (zoom == null || !zoom.isActive()) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue() * zoom.currentFovMultiplier());
    }
}
