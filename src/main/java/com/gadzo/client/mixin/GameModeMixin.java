package com.gadzo.client.mixin;

import com.gadzo.client.core.input.CombatTracker;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds landed attacks into the combo counter. */
@Mixin(MultiPlayerGameMode.class)
public class GameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void gadzo$trackCombo(Player attacker, Entity target, CallbackInfo ci) {
        CombatTracker.onAttack(target.getId());
    }
}
