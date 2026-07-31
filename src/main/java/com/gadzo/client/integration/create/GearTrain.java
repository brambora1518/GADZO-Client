package com.gadzo.client.integration.create;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out how to get from one rotation speed to another in Create.
 *
 * <p>The thing this exists to make obvious is that Create's gearing is binary. A large cogwheel
 * driving a small one doubles the speed, a small one driving a large one halves it, and every
 * other transmission block — shafts, belts, gearboxes, chain drives — passes the speed through
 * unchanged. So the only ratios a cogwheel chain can reach are powers of two.
 *
 * <p>That is not obvious from the blocks themselves, and it is the source of a lot of wasted
 * building: someone with a 16 RPM water wheel wanting 45 RPM will happily spend an afternoon
 * adding cogwheels that can only ever produce 32 or 64. The honest answer is that they need a
 * Rotational Speed Controller, and this planner says so rather than offering a near miss.
 */
public final class GearTrain {

    /** Create's own ceiling, {@code maxRotationSpeed}. Past this, shafts pop. */
    public static final int MAX_RPM = 256;

    /** Below this the network is treated as stopped. */
    public static final double MIN_RPM = 1.0;

    /** One cogwheel meeting another, and what it does to the speed. */
    public enum Step {
        SPEED_UP("Large cogwheel → small cogwheel", 2.0),
        SLOW_DOWN("Small cogwheel → large cogwheel", 0.5);

        private final String description;
        private final double factor;

        Step(String description, double factor) {
            this.description = description;
            this.factor = factor;
        }

        public String description() {
            return description;
        }

        public double factor() {
            return factor;
        }
    }

    /**
     * The outcome of a request.
     *
     * @param steps      cogwheel pairs to build, in order from the source
     * @param resultRpm  speed the chain actually produces
     * @param exact      whether {@code resultRpm} equals the requested target
     * @param note       the one thing the player most needs to know
     */
    public record Plan(List<Step> steps, double resultRpm, boolean exact, String note) {

        public boolean needsSpeedController() {
            return !exact;
        }

        /** Whether the chain drives the output backwards relative to the source. */
        public boolean reversesDirection() {
            // Every cogwheel mesh flips direction; an even number of them cancels out.
            return steps.size() % 2 == 1;
        }
    }

    private GearTrain() {
    }

    /**
     * Plans a chain from {@code sourceRpm} to {@code targetRpm}.
     *
     * <p>Halving and doubling are applied greedily, which is optimal here because there is only
     * one operation in each direction and no reason to ever mix them.
     */
    public static Plan solve(double sourceRpm, double targetRpm) {
        double source = Math.abs(sourceRpm);
        double target = Math.abs(targetRpm);

        if (source < MIN_RPM) {
            return new Plan(List.of(), 0, false,
                    "The source is not turning. Gearing multiplies zero by zero — you need a "
                            + "generator before a ratio.");
        }
        if (target < MIN_RPM) {
            return new Plan(List.of(), source, false,
                    "A target under 1 RPM counts as stopped in Create. Use a clutch to stop a "
                            + "network rather than gearing it down.");
        }
        if (target > MAX_RPM) {
            return new Plan(List.of(), source, false,
                    "Create caps rotation at " + MAX_RPM + " RPM. Anything faster snaps the "
                            + "shafts and stalls the network.");
        }

        List<Step> steps = new ArrayList<>();
        double current = source;

        // Double until another doubling would overshoot the target.
        while (current * 2 <= target + 1e-6 && current * 2 <= MAX_RPM) {
            steps.add(Step.SPEED_UP);
            current *= 2;
        }
        // Halve while we are still above it.
        while (current / 2 >= target - 1e-6 && current / 2 >= MIN_RPM) {
            steps.add(Step.SLOW_DOWN);
            current /= 2;
        }

        boolean exact = Math.abs(current - target) < 1e-6;
        String note;
        if (exact) {
            note = steps.isEmpty()
                    ? "Already at the target — a shaft or belt carries the speed unchanged."
                    : describeChain(steps, source, current);
        } else {
            note = String.format(
                    "Cogwheels can only double or halve, so %.0f RPM cannot be reached from "
                            + "%.0f RPM. The closest is %.0f RPM. For an exact %.0f, use a "
                            + "Rotational Speed Controller — it sets any speed from 1 to %d "
                            + "directly, at the cost of stress scaled to the speed you pick.",
                    target, source, current, target, MAX_RPM);
        }
        return new Plan(List.copyOf(steps), current, exact, note);
    }

    private static String describeChain(List<Step> steps, double source, double result) {
        long speedUps = steps.stream().filter(step -> step == Step.SPEED_UP).count();
        long slowDowns = steps.size() - speedUps;

        String direction = speedUps > 0
                ? speedUps + "× large-to-small"
                : slowDowns + "× small-to-large";

        return String.format("%.0f RPM → %.0f RPM with %s. Gear boxes and belts between the "
                + "pairs cost nothing extra; only the cogwheel meshes change the ratio.",
                source, result, direction);
    }

    /**
     * The other half of the question: what the ratio does to stress.
     *
     * <p>Worth stating explicitly next to any ratio plan, because the intuition from real
     * machinery is exactly wrong here. Gearing up does not trade torque for speed — it raises
     * consumption and capacity by the same factor, so the load ratio is untouched.
     */
    public static String stressEffect(double factor) {
        if (Math.abs(factor - 1) < 1e-6) {
            return "Speed unchanged, so stress is unchanged.";
        }
        return String.format("Running %.2f× faster multiplies both the stress drawn and the "
                + "capacity supplied by %.2f. The ratio of the two — whether you are "
                + "overstressed — does not move.", factor, factor);
    }

    /** Sails needed before a windmill turns at all, {@code minimumWindmillSails}. */
    public static final int WINDMILL_MINIMUM_SAILS = 8;

    /** Sails per RPM, {@code windmillSailsPerRPM}. */
    public static final int WINDMILL_SAILS_PER_RPM = 8;

    /** A windmill bearing's own speed ceiling, well below the global one. */
    public static final int WINDMILL_MAX_RPM = 16;

    /**
     * What a given sail count gets you on a windmill.
     *
     * <p>The failure mode worth guarding against is not slowness but silence: one sail short of
     * the minimum and the bearing does not turn at all, with nothing on screen to say why.
     *
     * <p>The ceiling is the other half of it. A windmill tops out at {@value #WINDMILL_MAX_RPM}
     * RPM, so past 128 sails the structure is costing chunk updates for no extra output.
     */
    public static String windmillAdvice(int sails) {
        if (sails < WINDMILL_MINIMUM_SAILS) {
            return "A windmill needs at least " + WINDMILL_MINIMUM_SAILS + " sails to turn at "
                    + "all. With " + sails + " the bearing stays still and gives no warning.";
        }
        int rpm = Math.min(sails / WINDMILL_SAILS_PER_RPM, WINDMILL_MAX_RPM);
        int sailsForMax = WINDMILL_SAILS_PER_RPM * WINDMILL_MAX_RPM;

        String base = sails + " sails give " + rpm + " RPM — " + WINDMILL_SAILS_PER_RPM
                + " sails per RPM. Sails must form one connected structure on the bearing, and "
                + "blocks that are not sails count towards the size limit without adding speed.";

        if (sails > sailsForMax) {
            return base + " This is already past the " + WINDMILL_MAX_RPM + " RPM cap; the extra "
                    + (sails - sailsForMax) + " sails do nothing. Gear up from a smaller windmill "
                    + "instead.";
        }
        return base;
    }
}
