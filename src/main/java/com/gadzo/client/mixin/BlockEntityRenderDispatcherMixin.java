package com.gadzo.client.mixin;

import com.gadzo.client.modules.performance.BlockEntityLimiter;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the block-entity distance limit.
 *
 * <p>The dispatcher's own {@code camera} field is used rather than the game renderer's, so the
 * distance is measured against the same viewpoint the renderer is drawing from — they differ
 * during a camera transition, and a mismatch would make distant machines flicker.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Shadow
    public Camera camera;

    @Inject(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void gadzo$limitRenderDistance(BlockEntity blockEntity, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        BlockEntityLimiter limiter = BlockEntityLimiter.get();
        if (limiter != null && limiter.shouldSkip(blockEntity, camera)) {
            ci.cancel();
        }
    }
}
