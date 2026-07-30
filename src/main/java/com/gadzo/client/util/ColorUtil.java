package com.gadzo.client.util;

/**
 * Packed ARGB colour helpers.
 *
 * <p>Colours are plain {@code int}s in {@code 0xAARRGGBB} form, matching what the vanilla
 * {@code fill}/{@code text} calls expect, so nothing here needs boxing on the render path.
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /** Replaces the alpha channel, leaving RGB untouched. */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((MathUtil.clamp(alpha, 0, 255)) << 24);
    }

    /** Scales the existing alpha by {@code factor}, used for fade-in/out of whole panels. */
    public static int fade(int color, double factor) {
        int scaled = (int) Math.round(alpha(color) * MathUtil.clamp(factor, 0.0, 1.0));
        return withAlpha(color, scaled);
    }

    /** Straight component-wise blend; {@code delta} of 0 returns {@code from}. */
    public static int mix(int from, int to, double delta) {
        double t = MathUtil.clamp(delta, 0.0, 1.0);
        return argb(
                (int) Math.round(MathUtil.lerp(alpha(from), alpha(to), t)),
                (int) Math.round(MathUtil.lerp(red(from), red(to), t)),
                (int) Math.round(MathUtil.lerp(green(from), green(to), t)),
                (int) Math.round(MathUtil.lerp(blue(from), blue(to), t)));
    }

    /** Multiplies RGB by {@code factor}; values above 1 lighten, below 1 darken. */
    public static int brightness(int color, double factor) {
        return argb(
                alpha(color),
                MathUtil.clamp((int) Math.round(red(color) * factor), 0, 255),
                MathUtil.clamp((int) Math.round(green(color) * factor), 0, 255),
                MathUtil.clamp((int) Math.round(blue(color) * factor), 0, 255));
    }

    /**
     * Perceived luminance in {@code [0, 1]} using Rec. 601 weights. Used to decide whether
     * a surface needs light or dark text on top of it.
     */
    public static double luminance(int color) {
        return (0.299 * red(color) + 0.587 * green(color) + 0.114 * blue(color)) / 255.0;
    }

    /** Picks black or white text for maximum contrast against {@code background}. */
    public static int contrastingText(int background) {
        return luminance(background) > 0.55 ? 0xFF11151C : 0xFFFFFFFF;
    }

    /**
     * HSB to packed ARGB.
     *
     * <p>Implemented locally rather than via {@code java.awt.Color} so the render path never
     * touches AWT, which is awkward on headless and macOS clients.
     *
     * @param hue wraps around, so any real value is legal
     * @param saturation clamped to {@code [0, 1]}
     * @param brightness clamped to {@code [0, 1]}
     */
    public static int hsb(float hue, float saturation, float brightness, int alpha) {
        float h = hue - (float) Math.floor(hue);
        float s = (float) MathUtil.clamp(saturation, 0.0f, 1.0f);
        float v = (float) MathUtil.clamp(brightness, 0.0f, 1.0f);

        int sector = (int) (h * 6.0f) % 6;
        float offset = h * 6.0f - (float) Math.floor(h * 6.0f);
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * offset);
        float t = v * (1.0f - s * (1.0f - offset));

        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return argb(alpha, Math.round(r * 255.0f), Math.round(g * 255.0f), Math.round(b * 255.0f));
    }

    /** Hue-cycling colour used by the "Rainbow" accent mode. */
    public static int rainbow(long periodMillis, float saturation, float brightness, int alpha, int offsetMillis) {
        float hue = ((System.currentTimeMillis() + offsetMillis) % periodMillis) / (float) periodMillis;
        return hsb(hue, saturation, brightness, alpha);
    }

    /** Parses {@code #RRGGBB} or {@code #AARRGGBB}; returns {@code fallback} when malformed. */
    public static int parseHex(String text, int fallback) {
        if (text == null) return fallback;
        String cleaned = text.trim();
        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
        try {
            if (cleaned.length() == 6) {
                return 0xFF000000 | Integer.parseInt(cleaned, 16);
            }
            if (cleaned.length() == 8) {
                return (int) Long.parseLong(cleaned, 16);
            }
        } catch (NumberFormatException ignored) {
            // fall through to the fallback
        }
        return fallback;
    }

    public static String toHex(int color) {
        return String.format("#%08X", color);
    }
}
