package com.gadzo.client.integration.create;

/**
 * A snapshot of one kinetic block and the network it belongs to.
 *
 * <p>Stress and capacity are absolute Stress Units, matching what the Engineer's Goggles show,
 * not the per-RPM figures the planner works in. Create stores the network totals already
 * multiplied by speed, and reporting them any other way would mean this readout and the goggles
 * disagreed on the same network.
 *
 * @param blockName            display name of the block that was looked at
 * @param speed                current RPM; the sign carries rotation direction
 * @param theoreticalSpeed     RPM the network would run at, ignoring an overstress stall
 * @param stress               Stress Units the network is consuming
 * @param capacity             Stress Units the network can supply
 * @param networkSize          number of blocks in the network
 * @param overStressed         whether the network has stalled under load
 * @param speedRequirementMet  whether this block is turning fast enough to do its job
 * @param connected            whether the block is attached to a network at all
 * @param impactPerRpm         this block's own stress draw, per RPM
 * @param capacityPerRpm       capacity this block adds, per RPM; zero for non-generators
 */
public record KineticReading(
        String blockName,
        float speed,
        float theoreticalSpeed,
        float stress,
        float capacity,
        int networkSize,
        boolean overStressed,
        boolean speedRequirementMet,
        boolean connected,
        double impactPerRpm,
        double capacityPerRpm) {

    /** Create's own speed tiers, which decide whether a machine can run at all. */
    public enum SpeedTier {
        STOPPED("Stopped"),
        SLOW("Slow"),
        MEDIUM("Medium"),
        FAST("Fast");

        private final String label;

        SpeedTier(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** RPM regardless of direction, which is what every threshold in Create is measured on. */
    public float absoluteSpeed() {
        return Math.abs(speed);
    }

    /** True when the shaft is turning the other way. Only meaningful when moving. */
    public boolean reversed() {
        return speed < 0;
    }

    public boolean stopped() {
        return absoluteSpeed() < 0.01f;
    }

    /**
     * Fraction of the network's capacity in use.
     *
     * <p>An unpowered network has no capacity, so the ratio is reported as zero rather than as
     * a division by zero — a stopped network is not overloaded, it is simply off.
     */
    public double load() {
        if (capacity <= 0) {
            return stress > 0 ? Double.POSITIVE_INFINITY : 0;
        }
        return stress / capacity;
    }

    /** Stress Units still available, floored at zero. */
    public float headroom() {
        return Math.max(0, capacity - stress);
    }

    /**
     * Speed tier, using Create's default thresholds of 30 and 100 RPM.
     *
     * <p>Both are server-configurable and this does not read the config, so a pack that
     * retunes them will see the label drift while the raw RPM figure stays correct.
     */
    public SpeedTier tier() {
        float rpm = absoluteSpeed();
        if (rpm < 0.01f) {
            return SpeedTier.STOPPED;
        }
        if (rpm >= 100) {
            return SpeedTier.FAST;
        }
        return rpm >= 30 ? SpeedTier.MEDIUM : SpeedTier.SLOW;
    }

    /** Whether this block is a generator rather than a consumer. */
    public boolean isGenerator() {
        return capacityPerRpm > 0;
    }

    /**
     * The single most useful sentence about the current state, for a one-line readout.
     *
     * <p>Ordered by what blocks the player first: a network that is not connected cannot be
     * overstressed, and one that is overstressed will never meet a speed requirement, so
     * reporting the later problems first would send them chasing the wrong thing.
     */
    public String diagnosis() {
        if (!connected) {
            return "Not connected to a network";
        }
        if (overStressed) {
            return "Overstressed — add generators or remove machines";
        }
        if (stopped()) {
            return "No rotation — the network has no working generator";
        }
        if (!speedRequirementMet) {
            return "Too slow for this machine — gear up";
        }
        return "Running";
    }

    /** True when something is wrong and worth colouring red. */
    public boolean isProblem() {
        return !connected || overStressed || stopped() || !speedRequirementMet;
    }
}
