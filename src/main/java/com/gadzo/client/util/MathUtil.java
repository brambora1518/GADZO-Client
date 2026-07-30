package com.gadzo.client.util;

/** Small numeric helpers shared across rendering, HUD layout and settings. */
public final class MathUtil {

    private MathUtil() {
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double lerp(double from, double to, double delta) {
        return from + (to - from) * delta;
    }

    public static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }

    /**
     * Frame-rate independent approach towards {@code target}.
     *
     * <p>A plain {@code lerp} per frame moves faster on high-refresh displays. This variant
     * folds the frame time into the exponent so a 30 fps and a 240 fps client converge over
     * the same wall-clock duration.
     *
     * @param speed fraction of the remaining distance covered per second, in {@code [0, 1)}
     * @param deltaSeconds elapsed wall-clock time since the previous frame
     */
    public static double approach(double current, double target, double speed, double deltaSeconds) {
        if (deltaSeconds <= 0.0) return current;
        double factor = 1.0 - Math.pow(1.0 - clamp(speed, 0.0, 0.9999), deltaSeconds * 60.0);
        return current + (target - current) * factor;
    }

    /** Maps {@code value} from one range onto another without clamping. */
    public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        if (inMax - inMin == 0.0) return outMin;
        return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
    }

    /** Rounds to the nearest multiple of {@code step}, used by slider snapping. */
    public static double roundToStep(double value, double step) {
        if (step <= 0.0) return value;
        return Math.round(value / step) * step;
    }

    /** Rounds to {@code decimals} places for tidy slider labels. */
    public static double round(double value, int decimals) {
        double factor = Math.pow(10.0, decimals);
        return Math.round(value * factor) / factor;
    }

    public static boolean within(double x, double y, double left, double top, double right, double bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}
