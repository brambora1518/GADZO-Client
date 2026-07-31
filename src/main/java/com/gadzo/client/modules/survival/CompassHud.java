package com.gadzo.client.modules.survival;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.survival.Waypoint;
import com.gadzo.client.survival.WaypointStore;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * A compass strip with waypoint markers.
 *
 * <p>Cardinal letters slide past as you turn, and each waypoint in this dimension shows as a
 * coloured tick at its bearing. This is the half of the waypoint system that works regardless
 * of terrain: a beam behind a mountain tells you nothing, whereas a tick on a compass tells you
 * which way to walk.
 *
 * <p>Markers outside the visible arc are pinned to the edge with an arrow rather than dropped,
 * because "it is behind you" is the single most useful thing a compass can say.
 */
public class CompassHud extends HudModule {

    private static final double HEIGHT = 20.0;

    /** Cardinal points in Minecraft's yaw convention: 0 is south, 90 is west. */
    private static final double[] CARDINAL_YAW = {0, 45, 90, 135, 180, -135, -90, -45};
    private static final String[] CARDINAL_NAME = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private final NumberSetting width;
    private final NumberSetting fieldOfView;
    private final BooleanSetting showWaypoints;
    private final BooleanSetting showDegrees;
    private final BooleanSetting pinOffscreen;

    public CompassHud() {
        super("Compass", "Direction strip with waypoint bearings", ModuleCategory.SURVIVAL,
                HudAnchor.TOP_CENTER, 0, 6);
        this.width = addNumber("Width", 180, 80, 400, 10, "How wide the strip is")
                .suffix(" px");
        this.fieldOfView = addNumber("Arc", 180, 60, 320, 10,
                "How many degrees of heading the strip covers")
                .suffix("°");
        this.showWaypoints = addBool("Waypoints", true, "Mark waypoint bearings on the strip");
        this.showDegrees = addBool("Heading", true, "Show the numeric heading");
        this.pinOffscreen = addBool("Pin off-screen", true,
                "Keep markers behind you at the edge instead of hiding them");
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return width.get();
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return HEIGHT;
    }

    /**
     * Horizontal position for a bearing, or {@code NaN} when it is outside the arc.
     *
     * <p>The caller decides what to do with an off-arc bearing; returning NaN rather than
     * clamping keeps that decision in one place instead of silently drawing everything at the
     * edges.
     */
    private double screenX(double bearing, double playerYaw, double strip) {
        double delta = MathHelper.wrapDegrees(bearing - playerYaw);
        double arc = fieldOfView.get();
        if (Math.abs(delta) > arc / 2) {
            return Double.NaN;
        }
        return strip / 2 + delta / arc * strip;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return;
        }
        double strip = width.get();
        double yaw = MathHelper.wrapDegrees(player.getYaw());

        // Baseline the ticks hang from.
        Render2D.rect(gfx, 0, HEIGHT - 5, strip, 1, ColorUtil.withAlpha(Theme.border(), 200));

        drawCardinals(gfx, font, strip, yaw);

        if (showWaypoints.get()) {
            drawWaypoints(gfx, font, strip, yaw, player.getPos());
        }

        // Centre marker, drawn last so nothing overlaps the thing you are actually facing.
        Render2D.rect(gfx, strip / 2 - 0.5, HEIGHT - 9, 1, 5, Theme.accent());

        if (showDegrees.get()) {
            String heading = Math.round(MathHelper.wrapDegrees(yaw + 180)) + "°";
            Render2D.textCentered(gfx, font, heading, strip / 2, HEIGHT - 5 + 1,
                    Theme.textMuted(), hasTextShadow());
        }
    }

    private void drawCardinals(DrawContext gfx, TextRenderer font, double strip, double yaw) {
        for (int i = 0; i < CARDINAL_YAW.length; i++) {
            double x = screenX(CARDINAL_YAW[i], yaw, strip);
            if (Double.isNaN(x)) {
                continue;
            }
            boolean major = CARDINAL_NAME[i].length() == 1;
            Render2D.rect(gfx, x - 0.5, HEIGHT - (major ? 9 : 7), 1, major ? 4 : 2,
                    major ? Theme.textSecondary() : Theme.textMuted());
            if (major) {
                Render2D.textCentered(gfx, font, CARDINAL_NAME[i], x, 0,
                        cardinalColor(CARDINAL_NAME[i]), hasTextShadow());
            }
        }
    }

    /** North is highlighted; the rest are plain. Everyone orients from north. */
    private int cardinalColor(String name) {
        return name.equals("N") ? Theme.accent() : Theme.textSecondary();
    }

    private void drawWaypoints(DrawContext gfx, TextRenderer font, double strip, double yaw,
                               Vec3d position) {
        List<Waypoint> waypoints = WaypointStore.nearest(position, 6);

        for (Waypoint waypoint : waypoints) {
            if (!waypoint.visible()) {
                continue;
            }
            double bearing = waypoint.bearingFrom(position);
            double x = screenX(bearing, yaw, strip);

            boolean offArc = Double.isNaN(x);
            if (offArc) {
                if (!pinOffscreen.get()) {
                    continue;
                }
                // Pin to whichever edge is the shorter way to turn.
                double delta = MathHelper.wrapDegrees(bearing - yaw);
                x = delta > 0 ? strip - 2 : 2;
            }

            Render2D.rect(gfx, x - 1, HEIGHT - 10, 2, 6, waypoint.color());

            String label = offArc ? edgeArrow(bearing, yaw) : shortLabel(waypoint, position);
            Render2D.textCentered(gfx, font, label, x, HEIGHT - 10 - font.fontHeight - 1,
                    waypoint.color(), hasTextShadow());
        }
    }

    private String edgeArrow(double bearing, double yaw) {
        return MathHelper.wrapDegrees(bearing - yaw) > 0 ? ">" : "<";
    }

    /** Distance in whole blocks, or in kilometres once that stops being readable. */
    private String shortLabel(Waypoint waypoint, Vec3d position) {
        double distance = waypoint.horizontalDistanceTo(position);
        return distance >= 1000
                ? String.format("%.1fk", distance / 1000)
                : String.valueOf(Math.round(distance));
    }
}
