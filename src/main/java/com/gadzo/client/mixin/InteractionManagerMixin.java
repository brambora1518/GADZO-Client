package com.gadzo.client.mixin;

import com.gadzo.client.core.input.CombatTracker;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds landed attacks into the combo counter. */
@Mixin(ClientPlayerInteractionManager.class)
public class InteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void gadzo$trackCombo(PlayerEntity attacker, Entity target, CallbackInfo ci) {
        CombatTracker.onAttack(target.getId());
    }
}
