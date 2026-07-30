package com.gadzo.client.util;

/**
 * A scalar that eases towards a target over wall-clock time.
 *
 * <p>Every hover highlight, panel slide and toggle knob in the client owns one of these.
 * Progress is driven by {@link System#nanoTime()} rather than by tick counts, so animations
 * run at the same speed regardless of frame rate or whether the game is paused.
 */
public class Animation {

    private final long durationNanos;
    private final Easing easing;

    private double origin;
    private double target;
    private long startedAt;

    public Animation(double initialValue, long durationMillis, Easing easing) {
        this.origin = initialValue;
        this.target = initialValue;
        this.durationNanos = Math.max(1L, durationMillis) * 1_000_000L;
        this.easing = easing;
        this.startedAt = System.nanoTime() - durationNanos;
    }

    public Animation(double initialValue, long durationMillis) {
        this(initialValue, durationMillis, Easing.EXPO_OUT);
    }

    /**
     * Redirects the animation towards {@code newTarget}.
     *
     * <p>Retargeting mid-flight restarts the curve from wherever the value currently sits,
     * which keeps direction changes smooth instead of snapping back to the old origin.
     */
    public void to(double newTarget) {
        if (Double.compare(this.target, newTarget) == 0) {
            return;
        }
        this.origin = value();
        this.target = newTarget;
        this.startedAt = System.nanoTime();
    }

    /** Jumps straight to {@code newValue} with no interpolation. */
    public void snapTo(double newValue) {
        this.origin = newValue;
        this.target = newValue;
        this.startedAt = System.nanoTime() - durationNanos;
    }

    public double value() {
        double progress = rawProgress();
        if (progress >= 1.0) {
            return target;
        }
        return origin + (target - origin) * easing.apply(progress);
    }

    public float floatValue() {
        return (float) value();
    }

    public double target() {
        return target;
    }

    public boolean isFinished() {
        return rawProgress() >= 1.0;
    }

    /** Convenience for the common "animate 0 -> 1 on hover" case. */
    public void toBoolean(boolean on) {
        to(on ? 1.0 : 0.0);
    }

    private double rawProgress() {
        return MathUtil.clamp((System.nanoTime() - startedAt) / (double) durationNanos, 0.0, 1.0);
    }
}
