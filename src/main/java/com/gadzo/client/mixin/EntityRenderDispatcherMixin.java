package com.gadzo.client.mixin;

import com.gadzo.client.modules.performance.EntityCulling;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the entity-culling module's decision.
 *
 * <p>Injected at HEAD and cancelling so a culled entity costs nothing beyond the distance
 * check itself.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void gadzo$cullDistantEntities(
            E entity, Frustum frustum, double cameraX, double cameraY, double cameraZ,
            CallbackInfoReturnable<Boolean> cir) {
        EntityCulling culling = EntityCulling.get();
        if (culling != null && culling.shouldCull(entity)) {
            cir.setReturnValue(false);
        }
    }
}
