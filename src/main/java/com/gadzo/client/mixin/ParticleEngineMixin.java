package com.gadzo.client.mixin;

import com.gadzo.client.modules.performance.ParticleLimiter;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enforces the particle spawn budget and refills it each tick. */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void gadzo$budgetSpawns(Particle particle, CallbackInfo ci) {
        ParticleLimiter limiter = ParticleLimiter.get();
        if (limiter != null && limiter.rejectSpawn()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void gadzo$refillBudget(CallbackInfo ci) {
        ParticleLimiter limiter = ParticleLimiter.get();
        if (limiter != null) {
            limiter.resetTickBudget();
        }
    }
}
