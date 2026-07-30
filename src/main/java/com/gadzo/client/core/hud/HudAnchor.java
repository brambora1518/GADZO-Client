package com.gadzo.client.core.hud;

/**
 * Which corner or edge a HUD element is positioned relative to.
 *
 * <p>Anchoring rather than storing absolute pixels is what keeps a HUD layout intact when the
 * player changes resolution or GUI scale: an element pinned to {@code TOP_RIGHT} stays in the
 * top right instead of drifting off-screen.
 */
public enum HudAnchor {
    TOP_LEFT(0.0, 0.0),
    TOP_CENTER(0.5, 0.0),
    TOP_RIGHT(1.0, 0.0),
    MIDDLE_LEFT(0.0, 0.5),
    MIDDLE_CENTER(0.5, 0.5),
    MIDDLE_RIGHT(1.0, 0.5),
    BOTTOM_LEFT(0.0, 1.0),
    BOTTOM_CENTER(0.5, 1.0),
    BOTTOM_RIGHT(1.0, 1.0);

    private final double xFactor;
    private final double yFactor;

    HudAnchor(double xFactor, double yFactor) {
        this.xFactor = xFactor;
        this.yFactor = yFactor;
    }

    public double xFactor() {
        return xFactor;
    }

    public double yFactor() {
        return yFactor;
    }

    /** Absolute x of this anchor's origin on a screen of the given width. */
    public double originX(int screenWidth) {
        return screenWidth * xFactor;
    }

    public double originY(int screenHeight) {
        return screenHeight * yFactor;
    }

    /**
     * Picks the anchor whose origin is closest to a point.
     *
     * <p>Used by the HUD editor: after a drag, the element re-anchors to whichever corner it
     * was dropped nearest, so it keeps behaving sensibly on other resolutions.
     */
    public static HudAnchor nearest(double x, double y, int screenWidth, int screenHeight) {
        HudAnchor best = TOP_LEFT;
        double bestDistance = Double.MAX_VALUE;
        for (HudAnchor anchor : values()) {
            double dx = x - anchor.originX(screenWidth);
            double dy = y - anchor.originY(screenHeight);
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = anchor;
            }
        }
        return best;
    }
}
