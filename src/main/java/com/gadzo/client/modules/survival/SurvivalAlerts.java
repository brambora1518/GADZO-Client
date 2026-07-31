package com.gadzo.client.modules.survival;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.ui.notify.Notifications;
import com.gadzo.client.util.Mc;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Warnings for the things that end a survival run.
 *
 * <p>Every alert here fires on a threshold being <em>crossed</em>, not on the condition being
 * true. A warning that repeats every tick while you are at four hearts is noise, and noise is
 * how people learn to ignore the warning that mattered.
 *
 * <p>Crossing back above a threshold re-arms it, so taking a hit down to three hearts, healing,
 * and being hit again warns twice — which is correct, because those are two separate moments of
 * danger.
 */
public class SurvivalAlerts extends Module {

    private final BooleanSetting lowHealth;
    private final NumberSetting healthThreshold;
    private final BooleanSetting lowDurability;
    private final NumberSetting durabilityThreshold;
    private final BooleanSetting lowAir;
    private final NumberSetting airThreshold;
    private final BooleanSetting lowFood;
    private final NumberSetting foodThreshold;

    /** Latches, so each condition warns once per crossing rather than once per tick. */
    private boolean healthArmed = true;
    private boolean airArmed = true;
    private boolean foodArmed = true;

    /** Items already warned about, by display name, cleared when they rise back above. */
    private final Set<String> warnedItems = new HashSet<>();

    public SurvivalAlerts() {
        super("Survival alerts", "Warn on low health, air, hunger and gear",
                ModuleCategory.SURVIVAL);
        this.lowHealth = addBool("Low health", true, "Warn when health drops");
        this.healthThreshold = addNumber("Health below", 6, 1, 19, 1,
                "Health points that trigger the warning")
                .suffix(" hp");
        this.lowDurability = addBool("Gear breaking", true,
                "Warn when a held or worn item is nearly spent");
        this.durabilityThreshold = addNumber("Durability below", 10, 1, 50, 1,
                "Remaining durability that triggers the warning")
                .suffix("%");
        this.lowAir = addBool("Drowning", true, "Warn when air runs low underwater");
        this.airThreshold = addNumber("Air below", 30, 5, 90, 5,
                "Remaining air that triggers the warning")
                .suffix("%");
        this.lowFood = addBool("Hungry", true, "Warn when hunger stops you sprinting");
        this.foodThreshold = addNumber("Hunger below", 6, 1, 17, 1,
                "Food level that triggers the warning")
                .suffix(" pts");
    }

    @Override
    protected void onEnable() {
        healthArmed = true;
        airArmed = true;
        foodArmed = true;
        warnedItems.clear();
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return;
        }
        checkHealth(player);
        checkAir(player);
        checkFood(player);
        checkDurability(player);
    }

    private void checkHealth(ClientPlayerEntity player) {
        if (!lowHealth.get()) {
            return;
        }
        float health = player.getHealth();
        float limit = (float) healthThreshold.get();

        if (health > limit) {
            healthArmed = true;
            return;
        }
        if (health <= 0 || !healthArmed) {
            return;
        }
        healthArmed = false;
        Notifications.error("Low health",
                String.format("%.1f hearts left.", health / 2));
    }

    private void checkAir(ClientPlayerEntity player) {
        if (!lowAir.get()) {
            return;
        }
        int max = player.getMaxAir();
        if (max <= 0) {
            return;
        }
        double fraction = player.getAir() / (double) max * 100;

        if (fraction > airThreshold.get()) {
            airArmed = true;
            return;
        }
        if (!airArmed) {
            return;
        }
        airArmed = false;
        Notifications.warning("Running out of air",
                Math.max(0, player.getAir() / 20) + " seconds of breath left.");
    }

    private void checkFood(ClientPlayerEntity player) {
        if (!lowFood.get()) {
            return;
        }
        int food = player.getHungerManager().getFoodLevel();
        if (food > foodThreshold.get()) {
            foodArmed = true;
            return;
        }
        if (!foodArmed) {
            return;
        }
        foodArmed = false;
        Notifications.warning("Hungry",
                food < 6 ? "Too hungry to sprint." : "Hunger is running low.");
    }

    private void checkDurability(ClientPlayerEntity player) {
        if (!lowDurability.get()) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(player.getMainHandStack());
        stacks.add(player.getOffHandStack());
        stacks.addAll(player.getInventory().armor);

        double limit = durabilityThreshold.get() / 100.0;
        Set<String> stillLow = new HashSet<>();

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || !stack.isDamageable()) {
                continue;
            }
            int max = stack.getMaxDamage();
            if (max <= 0) {
                continue;
            }
            double remaining = (max - stack.getDamage()) / (double) max;
            if (remaining > limit) {
                continue;
            }
            String name = stack.getName().getString();
            stillLow.add(name);
            if (warnedItems.add(name)) {
                Notifications.warning("Gear breaking",
                        name + " has " + Math.round(remaining * 100) + "% durability left.");
            }
        }
        // Anything repaired or swapped out is re-armed, so a fresh pickaxe warns again later.
        warnedItems.retainAll(stillLow);
    }
}
