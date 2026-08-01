package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.system.FrameTimeMonitor;
import com.gadzo.client.ui.Theme;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * How evenly the game is running, rather than how fast.
 *
 * <p>The FPS counter answers a question nobody has. Two clients both reporting 140 FPS can feel
 * completely different, and the difference is entirely in the frames that took far longer than
 * the rest. This shows those: the mean frame rate across the worst 1% and 0.1% of frames, how
 * many stutters happened in the last minute, and how much of that minute the garbage collector
 * spent holding the game still.
 *
 * <p>The stutter line is colour-coded by cause rather than by count, because the count tells
 * the player nothing they cannot already feel while the cause tells them what to change.
 */
public class SmoothnessHud extends HudModule {

    private final BooleanSetting showLows;
    private final BooleanSetting showTenth;
    private final BooleanSetting showStutters;
    private final BooleanSetting showGc;
    private final BooleanSetting hideWhenSmooth;

    /** Stutters a minute below which the readout is not worth the screen space. */
    private static final int QUIET_THRESHOLD = 3;

    public SmoothnessHud() {
        super("Smoothness", "1% lows, stutter count and collector pauses",
                HudAnchor.TOP_LEFT, 4, 24);
        this.showLows = addBool("1% low", true, "Mean frame rate across the worst 1% of frames");
        this.showTenth = addBool("0.1% low", false, "Mean frame rate across the worst 0.1%");
        this.showStutters = addBool("Stutters", true, "Long frames in the last minute");
        this.showGc = addBool("Collector time", true, "Milliseconds spent collecting per minute");
        this.hideWhenSmooth = addBool("Hide when smooth", false,
                "Only appear once the game is actually hitching");
    }

    private boolean quiet() {
        return hideWhenSmooth.get() && FrameTimeMonitor.spikesInWindow() < QUIET_THRESHOLD;
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(4);
        if (!FrameTimeMonitor.hasData()) {
            lines.add("measuring...");
            return lines;
        }
        if (quiet()) {
            return lines;
        }
        if (showLows.get()) {
            lines.add("1% low  " + Math.round(FrameTimeMonitor.lowOnePercent()));
        }
        if (showTenth.get()) {
            lines.add("0.1% low  " + Math.round(FrameTimeMonitor.lowTenthPercent()));
        }
        if (showStutters.get()) {
            int spikes = FrameTimeMonitor.spikesInWindow();
            int gc = FrameTimeMonitor.gcSpikesInWindow();
            lines.add(gc > 0
                    ? "stutter  " + spikes + "/min  (" + gc + " gc)"
                    : "stutter  " + spikes + "/min");
        }
        if (showGc.get()) {
            lines.add("collector  " + FrameTimeMonitor.gcMillisInWindow() + " ms/min");
        }
        return lines;
    }

    /**
     * Colour for one line, keyed off what the line is about.
     *
     * <p>The lows go red at the point the game stops feeling smooth, not at some fraction of
     * the average — 30 FPS of worst-case frames is unpleasant whether the average is 60 or 300.
     */
    private int colorFor(String line) {
        if (line.startsWith("1% low") || line.startsWith("0.1% low")) {
            double value = line.startsWith("0.1%")
                    ? FrameTimeMonitor.lowTenthPercent()
                    : FrameTimeMonitor.lowOnePercent();
            if (value >= 60) return Theme.success();
            if (value >= 30) return Theme.warning();
            return Theme.danger();
        }
        if (line.startsWith("stutter")) {
            int spikes = FrameTimeMonitor.spikesInWindow();
            if (spikes == 0) return Theme.success();
            if (spikes < 8) return Theme.warning();
            return Theme.danger();
        }
        if (line.startsWith("collector")) {
            long millis = FrameTimeMonitor.gcMillisInWindow();
            if (millis < 500) return Theme.textSecondary();
            if (millis < 2000) return Theme.warning();
            return Theme.danger();
        }
        return Theme.textSecondary();
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
        int count = lines().size();
        return count == 0 ? 0 : count * font.fontHeight + (count - 1) * 2.0;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        double y = 0;
        for (String value : lines()) {
            line(gfx, font, value, 0, y, colorFor(value));
            y += font.fontHeight + 2.0;
        }
    }
}
