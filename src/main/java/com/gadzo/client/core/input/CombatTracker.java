package com.gadzo.client.core.input;

/**
 * Tracks consecutive hits landed on the same target.
 *
 * <p>A combo survives as long as the player keeps hitting the same entity within
 * {@link #COMBO_TIMEOUT_MILLIS}. Switching targets or letting the timer lapse resets it.
 * Fed from a mixin on the attack path so it reflects real swings, not tick samples.
 */
public final class CombatTracker {

    /** Matches the window other clients use for their combo readouts. */
    private static final long COMBO_TIMEOUT_MILLIS = 3000L;

    private static int comboCount;
    private static int lastTargetId = -1;
    private static long lastHitAt;

    private CombatTracker() {
    }

    /** Called from the attack mixin with the entity that was struck. */
    public static synchronized void onAttack(int targetEntityId) {
        long now = System.currentTimeMillis();
        boolean sameTarget = targetEntityId == lastTargetId;
        boolean inTime = now - lastHitAt <= COMBO_TIMEOUT_MILLIS;

        comboCount = (sameTarget && inTime) ? comboCount + 1 : 1;
        lastTargetId = targetEntityId;
        lastHitAt = now;
    }

    /** The live combo, or 0 once the window has lapsed. */
    public static synchronized int combo() {
        if (comboCount > 0 && System.currentTimeMillis() - lastHitAt > COMBO_TIMEOUT_MILLIS) {
            comboCount = 0;
            lastTargetId = -1;
        }
        return comboCount;
    }

    public static synchronized void reset() {
        comboCount = 0;
        lastTargetId = -1;
        lastHitAt = 0;
    }
}
