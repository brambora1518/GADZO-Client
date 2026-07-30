package com.gadzo.client.modules.performance;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.NumberSetting;

/**
 * Caps how many particles may spawn per tick.
 *
 * <p>Vanilla's particle option is a coarse three-step setting and still lets a single
 * explosion or potion cloud spike the frame time. Budgeting spawns per tick bounds the worst
 * case directly: ambient effects are unaffected at normal rates, while a burst is clipped to
 * the budget instead of queueing thousands of short-lived particles.
 *
 * <p>A spawn budget rather than a live-count ceiling is deliberate — it needs no access to
 * the particle engine's internal collections, so it cannot fall out of sync with them.
 */
public class ParticleLimiter extends Module {

    private static ParticleLimiter instance;

    private final NumberSetting perTickBudget;

    /** Spawns allowed so far in the current tick; reset by the engine mixin each tick. */
    private int spawnedThisTick;

    /** Rolling count of particles dropped, surfaced in the module description. */
    private long droppedTotal;

    public ParticleLimiter() {
        super("Particle limiter", "Bound how many particles can spawn each tick",
                ModuleCategory.PERFORMANCE);
        instance = this;

        this.perTickBudget = addNumber("Spawns per tick", 80, 5, 1000, 5,
                "Particles beyond this many in a single tick are discarded");
    }

    public static ParticleLimiter get() {
        return instance;
    }

    /**
     * Records a spawn attempt and reports whether it must be discarded.
     *
     * <p>Called from the particle engine's add path, so it does no allocation.
     */
    public boolean rejectSpawn() {
        if (!isEnabled()) {
            return false;
        }
        if (spawnedThisTick >= perTickBudget.getInt()) {
            droppedTotal++;
            return true;
        }
        spawnedThisTick++;
        return false;
    }

    /** Called once per client tick to refill the budget. */
    public void resetTickBudget() {
        spawnedThisTick = 0;
    }

    public long getDroppedTotal() {
        return droppedTotal;
    }

    @Override
    protected void onDisable() {
        spawnedThisTick = 0;
    }
}
