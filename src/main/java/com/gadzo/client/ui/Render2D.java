package com.gadzo.client.ui;

import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Drawing primitives for the GADZO interface.
 *
 * <p>MinecraftClient 26's GUI is retained-mode: a screen submits draw commands to a
 * {@link DrawContext} which batches them into a render state. That layer gives us
 * axis-aligned rectangles, vertical gradients, text and scissors — but no rounded corners,
 * which every surface in this client needs.
 *
 * <p>Rounded shapes are therefore rasterised here on the CPU as horizontal spans: one
 * {@code fill} per scanline through the corner bands, plus a single large fill for the
 * straight middle. Span ends get a partial-alpha pixel whose coverage comes from the exact
 * circle equation, which is what removes the staircase edge. A radius-8 corner costs roughly
 * 48 fills — negligible for menus, and HUD elements deliberately default to small radii.
 */
public final class Render2D {

    /** Corner rows beyond this are not worth the fill count; keeps a stray setting sane. */
    private static final int MAX_RADIUS = 64;

    private Render2D() {
    }

    // -- rectangles -----------------------------------------------------------------

    public static void rect(DrawContext gfx, double x, double y, double width, double height, int color) {
        if (width <= 0 || height <= 0 || ColorUtil.alpha(color) == 0) {
            return;
        }
        gfx.fill(floor(x), floor(y), ceil(x + width), ceil(y + height), color);
    }

    /** Vertical gradient; {@code top} and {@code bottom} are both ARGB. */
    public static void gradientV(DrawContext gfx, double x, double y, double width, double height,
                                 int top, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        gfx.fillGradient(floor(x), floor(y), ceil(x + width), ceil(y + height), top, bottom);
    }

    /**
     * Horizontal gradient, built from vertical strips.
     *
     * <p>The GUI layer only offers a vertical gradient, so this walks the x axis in
     * {@code step}-wide columns. Two pixels per step is visually indistinguishable from one
     * at normal GUI scales and halves the draw calls.
     */
    public static void gradientH(DrawContext gfx, double x, double y, double width, double height,
                                 int left, int right) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int x0 = floor(x);
        int x1 = ceil(x + width);
        int y0 = floor(y);
        int y1 = ceil(y + height);
        int span = Math.max(1, x1 - x0);
        int step = span > 256 ? 3 : 2;

        for (int px = x0; px < x1; px += step) {
            int end = Math.min(px + step, x1);
            double t = (px - x0) / (double) span;
            gfx.fill(px, y0, end, y1, ColorUtil.mix(left, right, t));
        }
    }

    // -- rounded rectangles ----------------------------------------------------------

    public static void roundedRect(DrawContext gfx, double x, double y, double width, double height,
                                   double radius, int color) {
        roundedRect(gfx, x, y, width, height, radius, radius, radius, radius, color);
    }

    /**
     * Rounded rectangle with independent corner radii.
     *
     * <p>Per-corner control is what lets tabs, sidebars and stacked list rows share one
     * primitive: a sidebar rounds only its left corners, a selected list row only its outer
     * pair, and so on.
     */
    public static void roundedRect(DrawContext gfx, double x, double y, double width, double height,
                                   double topLeft, double topRight, double bottomRight, double bottomLeft,
                                   int color) {
        if (width <= 0 || height <= 0 || ColorUtil.alpha(color) == 0) {
            return;
        }

        double maxRadius = Math.min(width, height) / 2.0;
        double tl = clampRadius(topLeft, maxRadius);
        double tr = clampRadius(topRight, maxRadius);
        double br = clampRadius(bottomRight, maxRadius);
        double bl = clampRadius(bottomLeft, maxRadius);

        if (tl <= 0 && tr <= 0 && br <= 0 && bl <= 0) {
            rect(gfx, x, y, width, height, color);
            return;
        }

        int left = floor(x);
        int right = ceil(x + width);
        int top = floor(y);
        int bottom = ceil(y + height);

        int topBand = (int) Math.ceil(Math.max(tl, tr));
        int bottomBand = (int) Math.ceil(Math.max(bl, br));

        // Straight middle section in one fill.
        int middleTop = top + topBand;
        int middleBottom = bottom - bottomBand;
        if (middleBottom > middleTop) {
            gfx.fill(left, middleTop, right, middleBottom, color);
        }

        for (int row = 0; row < topBand; row++) {
            // Sample the circle at the row's centre for a symmetric, non-biased edge.
            double dy = topBand - row - 0.5;
            double insetLeft = cornerInset(tl, dy);
            double insetRight = cornerInset(tr, dy);
            span(gfx, left, right, top + row, insetLeft, insetRight, color);
        }

        for (int row = 0; row < bottomBand; row++) {
            double dy = bottomBand - row - 0.5;
            double insetLeft = cornerInset(bl, dy);
            double insetRight = cornerInset(br, dy);
            span(gfx, left, right, bottom - 1 - row, insetLeft, insetRight, color);
        }
    }

    /**
     * Emits one scanline of a rounded shape.
     *
     * <p>The solid interior is a single fill; the fractional pixel at each end is drawn
     * separately with proportional alpha, which is where the anti-aliasing comes from.
     */
    private static void span(DrawContext gfx, int left, int right, int rowY,
                             double insetLeft, double insetRight, int color) {
        double startX = left + insetLeft;
        double endX = right - insetRight;
        if (endX - startX <= 0) {
            return;
        }

        int solidStart = (int) Math.ceil(startX);
        int solidEnd = (int) Math.floor(endX);

        if (solidEnd > solidStart) {
            gfx.fill(solidStart, rowY, solidEnd, rowY + 1, color);
        }

        double leftCoverage = solidStart - startX;
        if (leftCoverage > 0.01) {
            gfx.fill(solidStart - 1, rowY, solidStart, rowY + 1, ColorUtil.fade(color, leftCoverage));
        }

        double rightCoverage = endX - solidEnd;
        if (rightCoverage > 0.01) {
            gfx.fill(solidEnd, rowY, solidEnd + 1, rowY + 1, ColorUtil.fade(color, rightCoverage));
        }
    }

    /** Horizontal distance from the box edge to the circle at vertical offset {@code dy}. */
    private static double cornerInset(double radius, double dy) {
        if (radius <= 0) {
            return 0.0;
        }
        if (dy >= radius) {
            return radius;
        }
        return radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
    }

    private static double clampRadius(double radius, double maxRadius) {
        return MathUtil.clamp(radius, 0.0, Math.min(maxRadius, MAX_RADIUS));
    }

    /** Rounded rectangle with a vertical gradient, rasterised row by row. */
    public static void roundedGradientV(DrawContext gfx, double x, double y, double width, double height,
                                        double radius, int top, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int y0 = floor(y);
        int y1 = ceil(y + height);
        int rows = Math.max(1, y1 - y0);
        double maxRadius = Math.min(width, height) / 2.0;
        double r = clampRadius(radius, maxRadius);
        int left = floor(x);
        int right = ceil(x + width);

        for (int row = 0; row < rows; row++) {
            int rowY = y0 + row;
            double distanceFromEdge = Math.min(row + 0.5, rows - row - 0.5);
            double inset = cornerInset(r, distanceFromEdge);
            int color = ColorUtil.mix(top, bottom, row / (double) rows);
            span(gfx, left, right, rowY, inset, inset, color);
        }
    }

    /** Stroked rounded rectangle drawn as an outer shape masked by an inner one. */
    public static void roundedOutline(DrawContext gfx, double x, double y, double width, double height,
                                      double radius, double thickness, int color) {
        if (thickness <= 0 || width <= 0 || height <= 0) {
            return;
        }
        double t = Math.min(thickness, Math.min(width, height) / 2.0);

        // Four straight edges plus the corner arcs, drawn as thin rounded bars. Cheaper and
        // crisper than filling the outer shape and punching out the inner one, which would
        // need a real stencil.
        double innerRadius = Math.max(0.0, radius - t);
        roundedRect(gfx, x, y, width, t, radius, radius, 0, 0, color);
        roundedRect(gfx, x, y + height - t, width, t, 0, 0, radius, radius, color);
        roundedRect(gfx, x, y + t, t, height - 2 * t, 0, 0, 0, 0, color);
        roundedRect(gfx, x + width - t, y + t, t, height - 2 * t, 0, 0, 0, 0, color);

        // Re-cut the inside of the corner bands so the arc reads as a stroke, not a wedge.
        if (innerRadius > 0) {
            int band = (int) Math.ceil(radius);
            for (int row = 0; row < band; row++) {
                double dy = band - row - 0.5;
                double outer = cornerInset(radius, dy);
                double inner = cornerInset(innerRadius, Math.max(0.0, dy - t));
                if (inner > outer) {
                    int left = floor(x);
                    int right = ceil(x + width);
                    span(gfx, left, right, floor(y) + row, outer, outer, color);
                    span(gfx, left, right, ceil(y + height) - 1 - row, outer, outer, color);
                }
            }
        }
    }

    /**
     * Soft drop shadow behind a rounded surface.
     *
     * <p>Approximated with concentric rounded rectangles of decreasing alpha. A real blur
     * would need an offscreen pass; at these radii the layered version is indistinguishable
     * and costs nothing outside the GUI batch.
     */
    public static void shadow(DrawContext gfx, double x, double y, double width, double height,
                              double radius, int spread, int color) {
        int layers = Math.max(1, spread);
        int baseAlpha = ColorUtil.alpha(color);
        for (int i = layers; i >= 1; i--) {
            // Quadratic falloff reads as a soft penumbra; linear looks like a flat halo.
            double falloff = (double) (layers - i + 1) / layers;
            int alpha = (int) Math.round(baseAlpha * falloff * falloff / layers * 1.8);
            if (alpha <= 0) {
                continue;
            }
            roundedRect(gfx, x - i, y - i + 1, width + i * 2, height + i * 2,
                    radius + i, ColorUtil.withAlpha(color, alpha));
        }
    }

    public static void circle(DrawContext gfx, double centerX, double centerY, double radius, int color) {
        roundedRect(gfx, centerX - radius, centerY - radius, radius * 2, radius * 2, radius, color);
    }

    /** A horizontal 1px rule, used between menu sections. */
    public static void separator(DrawContext gfx, double x, double y, double width, int color) {
        rect(gfx, x, y, width, 1, color);
    }

    // -- text ------------------------------------------------------------------------

    public static void text(DrawContext gfx, TextRenderer font, String value, double x, double y, int color) {
        gfx.drawText(font, value, floor(x), floor(y), color, false);
    }

    public static void textShadowed(DrawContext gfx, TextRenderer font, String value, double x, double y, int color) {
        gfx.drawText(font, value, floor(x), floor(y), color, true);
    }

    public static void textCentered(DrawContext gfx, TextRenderer font, String value,
                                    double centerX, double y, int color, boolean shadow) {
        gfx.drawText(font, value, floor(centerX - font.getWidth(value) / 2.0), floor(y), color, shadow);
    }

    public static void textRight(DrawContext gfx, TextRenderer font, String value,
                                 double rightX, double y, int color, boolean shadow) {
        gfx.drawText(font, value, floor(rightX - font.getWidth(value)), floor(y), color, shadow);
    }

    /** Truncates with an ellipsis so long module names cannot overflow their row. */
    public static String truncate(TextRenderer font, String value, int maxWidth) {
        if (font.getWidth(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int budget = maxWidth - font.getWidth(ellipsis);
        if (budget <= 0) {
            return ellipsis;
        }
        return font.trimToWidth(value, budget) + ellipsis;
    }

    // -- helpers ----------------------------------------------------------------------

    /**
     * Marks everything drawn before this point to be blurred.
     *
     * <p>This is the frosted-glass effect behind the menus: the world (and any panel already
     * submitted) is blurred, then the current stratum draws on top of it crisply.
     *
     * <p>The GUI render state permits only one blur per frame and throws on a second request.
     * Screens are expected to call this from {@code extractBackground}, replacing vanilla's
     * own blur rather than adding to it — but a backdrop effect must never be able to take
     * the game down, so a duplicate request is swallowed and the frame simply renders
     * unblurred.
     *
     * @return whether the blur was accepted
     */
    public static boolean blurBehind(DrawContext gfx) {
        // Minecraft 1.20.1 has no GUI backdrop blur — the frosted-glass effect arrived with
        // the render-state rework in later versions. Screens fall back to a dim overlay,
        // which is why Theme.blurEnabled() has no visible effect on this branch.
        return false;
    }

    /** Starts a new depth stratum so later draws sit cleanly above earlier ones. */
    public static void layer(DrawContext gfx) {
        // No depth strata in 1.20.1; draw order alone decides layering.
    }

    public static void pushScissor(DrawContext gfx, double x, double y, double width, double height) {
        gfx.enableScissor(floor(x), floor(y), ceil(x + width), ceil(y + height));
    }

    public static void popScissor(DrawContext gfx) {
        gfx.disableScissor();
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }
}
