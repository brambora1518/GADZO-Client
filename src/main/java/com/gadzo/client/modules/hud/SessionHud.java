package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Playtime and distance travelled this session.
 *
 * <p>Counts only time spent in a world, not time sat in menus, so the number reflects actual
 * play rather than how long the game has been open.
 */
public class SessionHud extends HudModule {

    private final BooleanSetting showPlaytime;
    private final BooleanSetting showDistance;

    private long playedMillis;
    private long lastTickAt;
    private double distanceWalked;

    private double lastX;
    private double lastZ;
    private boolean primed;

    public SessionHud() {
        super("Session", "Playtime and distance travelled", ModuleCategory.CLIENT,
                HudAnchor.BOTTOM_RIGHT, -4, -20);
        this.showPlaytime = addBool("Playtime", true, "Time spent in a world this session");
        this.showDistance = addBool("Distance", true, "Blocks travelled horizontally");
    }

    @Override
    protected void onEnable() {
        lastTickAt = System.currentTimeMillis();
        primed = false;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = Mc.player();
        long now = System.currentTimeMillis();

        if (player == null || Mc.client() == null || Mc.client().world == null) {
            // Out of world: stop the clock rather than counting menu time.
            lastTickAt = now;
            primed = false;
            return;
        }

        if (lastTickAt != 0) {
            long delta = now - lastTickAt;
            // Guard against a huge jump after a long pause or world load.
            if (delta > 0 && delta < 5000) {
                playedMillis += delta;
            }
        }
        lastTickAt = now;

        if (!primed) {
            lastX = player.getX();
            lastZ = player.getZ();
            primed = true;
            return;
        }

        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        double step = Math.sqrt(dx * dx + dz * dz);
        // A teleport or dimension change is not distance walked.
        if (step < 10.0) {
            distanceWalked += step;
        }
        lastX = player.getX();
        lastZ = player.getZ();
    }

    private String formatDuration() {
        long totalSeconds = playedMillis / 1000L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }

    private String formatDistance() {
        return distanceWalked >= 1000
                ? String.format("%.1f km", distanceWalked / 1000.0)
                : String.format("%.0f m", distanceWalked);
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(2);
        if (showPlaytime.get()) {
            lines.add("Played " + formatDuration());
        }
        if (showDistance.get()) {
            lines.add("Walked " + formatDistance());
        }
        return lines;
    }

    @Override
    public double contentWidth(TextRenderer font) {
        double widest = 0;
        for (String line : lines()) {
            widest = Math.max(widest, font.getWidth(line));
        }
        return widest;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return Math.max(0, lines().size()) * font.fontHeight;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        double y = 0;
        for (String text : lines()) {
            line(gfx, font, text, 0, y, Theme.textPrimary());
            y += font.fontHeight;
        }
    }
}
