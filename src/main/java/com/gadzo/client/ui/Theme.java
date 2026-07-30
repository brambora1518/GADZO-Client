package com.gadzo.client.ui;

import com.gadzo.client.util.ColorUtil;

/**
 * The client's colour palette.
 *
 * <p>Surfaces are numbered by elevation — {@link #background()} is the furthest back,
 * {@link #surfaceHigh()} the closest to the viewer — so panels stack with consistent
 * contrast without every screen hand-picking hex values. Accent colours come from
 * {@link AccentMode}, which is what the user actually configures.
 */
public final class Theme {

    /** How the accent colour is produced. */
    public enum AccentMode {
        STATIC("Static"),
        GRADIENT("Gradient"),
        RAINBOW("Rainbow");

        private final String label;

        AccentMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Light or dark base palette. */
    public enum Appearance {
        DARK("Dark"),
        MIDNIGHT("Midnight"),
        LIGHT("Light");

        private final String label;

        Appearance(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static Appearance appearance = Appearance.DARK;
    private static AccentMode accentMode = AccentMode.GRADIENT;
    private static int accentPrimary = 0xFF5B8CFF;
    private static int accentSecondary = 0xFF9B5BFF;
    private static int rainbowPeriodMillis = 6000;
    private static double cornerRadius = 8.0;
    private static boolean blurEnabled = true;

    private Theme() {
    }

    // -- configuration ----------------------------------------------------------------

    public static void setAppearance(Appearance value) {
        appearance = value;
    }

    public static Appearance appearance() {
        return appearance;
    }

    public static void setAccentMode(AccentMode value) {
        accentMode = value;
    }

    public static AccentMode accentMode() {
        return accentMode;
    }

    public static void setAccentPrimary(int color) {
        accentPrimary = color;
    }

    public static void setAccentSecondary(int color) {
        accentSecondary = color;
    }

    public static void setRainbowPeriod(int millis) {
        rainbowPeriodMillis = Math.max(500, millis);
    }

    public static void setCornerRadius(double radius) {
        cornerRadius = radius;
    }

    public static double radius() {
        return cornerRadius;
    }

    /** A smaller radius for nested elements, kept proportional to the main one. */
    public static double radiusSmall() {
        return Math.max(2.0, cornerRadius * 0.6);
    }

    public static void setBlurEnabled(boolean value) {
        blurEnabled = value;
    }

    public static boolean blurEnabled() {
        return blurEnabled;
    }

    // -- accent -----------------------------------------------------------------------

    /**
     * The accent colour right now.
     *
     * <p>In {@link AccentMode#RAINBOW} this changes every frame, so callers must not cache
     * it across frames.
     *
     * @param offsetMillis phase shift, letting a row of elements form a moving gradient
     */
    public static int accent(int offsetMillis) {
        return switch (accentMode) {
            case STATIC -> accentPrimary;
            case GRADIENT -> {
                // Ping-pong between the two accents so the sweep has no visible seam.
                double phase = ((System.currentTimeMillis() + offsetMillis) % (rainbowPeriodMillis * 2L))
                        / (double) rainbowPeriodMillis;
                double t = phase <= 1.0 ? phase : 2.0 - phase;
                yield ColorUtil.mix(accentPrimary, accentSecondary, t);
            }
            case RAINBOW -> ColorUtil.rainbow(rainbowPeriodMillis, 0.72f, 1.0f, 255, offsetMillis);
        };
    }

    public static int accent() {
        return accent(0);
    }

    /** The two ends of the accent sweep, for gradients that need both at once. */
    public static int accentStart() {
        return accentMode == AccentMode.RAINBOW
                ? ColorUtil.rainbow(rainbowPeriodMillis, 0.72f, 1.0f, 255, 0)
                : accentPrimary;
    }

    public static int accentEnd() {
        return accentMode == AccentMode.RAINBOW
                ? ColorUtil.rainbow(rainbowPeriodMillis, 0.72f, 1.0f, 255, rainbowPeriodMillis / 4)
                : accentSecondary;
    }

    // -- surfaces ---------------------------------------------------------------------

    public static int background() {
        return switch (appearance) {
            case DARK -> 0xF00E1117;
            case MIDNIGHT -> 0xF6070911;
            case LIGHT -> 0xF2F4F6FA;
        };
    }

    public static int surface() {
        return switch (appearance) {
            case DARK -> 0xFF141922;
            case MIDNIGHT -> 0xFF0C111C;
            case LIGHT -> 0xFFFFFFFF;
        };
    }

    public static int surfaceHigh() {
        return switch (appearance) {
            case DARK -> 0xFF1B2230;
            case MIDNIGHT -> 0xFF121A2A;
            case LIGHT -> 0xFFEDEFF5;
        };
    }

    /** Subtle fill for hovered rows. */
    public static int surfaceHover() {
        return switch (appearance) {
            case DARK -> 0xFF232C3D;
            case MIDNIGHT -> 0xFF18223A;
            case LIGHT -> 0xFFE2E6F0;
        };
    }

    public static int border() {
        return switch (appearance) {
            case DARK -> 0x33FFFFFF;
            case MIDNIGHT -> 0x2BFFFFFF;
            case LIGHT -> 0x1A000000;
        };
    }

    public static int shadowColor() {
        return appearance == Appearance.LIGHT ? 0x40000000 : 0x80000000;
    }

    // -- text -------------------------------------------------------------------------

    public static int textPrimary() {
        return appearance == Appearance.LIGHT ? 0xFF11151C : 0xFFF2F5FA;
    }

    public static int textSecondary() {
        return appearance == Appearance.LIGHT ? 0xFF5A6272 : 0xFF9AA5B8;
    }

    public static int textMuted() {
        return appearance == Appearance.LIGHT ? 0xFF8C93A1 : 0xFF636E82;
    }

    /** Fill for a control that is switched off. */
    public static int trackOff() {
        return appearance == Appearance.LIGHT ? 0xFFC9CFDC : 0xFF2C3546;
    }

    public static int success() {
        return 0xFF3ECF8E;
    }

    public static int warning() {
        return 0xFFF5A524;
    }

    public static int danger() {
        return 0xFFF5455C;
    }
}
