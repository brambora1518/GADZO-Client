package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rolling frame-time graph.
 *
 * <p>An average FPS number hides the thing that actually ruins gameplay: the occasional
 * 80 ms frame. Plotting frame time rather than frame rate makes those spikes obvious, because
 * a stutter is a tall bar instead of a brief dip in a number that has already been averaged
 * away. The 1% low readout is the same information as a single figure.
 */
public class FrametimeGraphHud extends HudModule {

    /** Ring buffer of recent frame times in milliseconds. */
    private final float[] samples = new float[240];

    private int writeIndex;
    private int sampleCount;
    private long lastFrameNanos;

    private final NumberSetting graphWidth;
    private final NumberSetting graphHeight;
    private final NumberSetting ceiling;
    private final BooleanSetting showLows;
    private final BooleanSetting showTargetLine;

    public FrametimeGraphHud() {
        super("Frametime graph", "Frame time history and 1% lows", ModuleCategory.PERFORMANCE,
                HudAnchor.BOTTOM_LEFT, 4, -4);
        this.graphWidth = addNumber("Width", 120, 60, 240, 10, "Graph width in pixels")
                .suffix(" px");
        this.graphHeight = addNumber("Height", 34, 20, 80, 2, "Graph height in pixels")
                .suffix(" px");
        this.ceiling = addNumber("Scale ceiling", 50, 16, 200, 2,
                "Frame time at the top of the graph")
                .suffix(" ms");
        this.showLows = addBool("1% lows", true, "Show average and 1% low frame time");
        this.showTargetLine = addBool("60 fps line", true, "Mark the 16.7 ms budget");
    }

    /**
     * Records the elapsed time since the previous frame.
     *
     * <p>Sampled during rendering rather than on tick, because ticks are fixed at 20 Hz and
     * would say nothing about frame pacing.
     */
    private void sample() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            float millis = (now - lastFrameNanos) / 1_000_000.0f;
            // Ignore absurd gaps: alt-tabbing or a world load is not a frame-time spike.
            if (millis < 1000.0f) {
                samples[writeIndex] = millis;
                writeIndex = (writeIndex + 1) % samples.length;
                sampleCount = Math.min(sampleCount + 1, samples.length);
            }
        }
        lastFrameNanos = now;
    }

    /** Sample {@code i} counting back from the most recent. */
    private float sampleAt(int stepsBack) {
        int index = Math.floorMod(writeIndex - 1 - stepsBack, samples.length);
        return samples[index];
    }

    private float average() {
        if (sampleCount == 0) return 0;
        float total = 0;
        for (int i = 0; i < sampleCount; i++) {
            total += sampleAt(i);
        }
        return total / sampleCount;
    }

    /**
     * The 1% low, as the mean of the worst one percent of frames.
     *
     * <p>Uses a partial selection rather than a full sort: the sample window is small and the
     * worst slice is tiny, so repeatedly taking the maximum is cheaper than ordering
     * everything each frame.
     */
    private float onePercentLow() {
        if (sampleCount < 10) return 0;
        int slice = Math.max(1, sampleCount / 100);
        float[] worst = new float[slice];
        for (int i = 0; i < sampleCount; i++) {
            float value = sampleAt(i);
            // Insert into the worst-list if it beats the smallest entry there.
            int smallest = 0;
            for (int j = 1; j < slice; j++) {
                if (worst[j] < worst[smallest]) smallest = j;
            }
            if (value > worst[smallest]) {
                worst[smallest] = value;
            }
        }
        float total = 0;
        for (float value : worst) total += value;
        return total / slice;
    }

    private int barColor(float millis) {
        if (millis <= 8.33f) return Theme.success();
        if (millis <= 16.7f) return Theme.accent();
        if (millis <= 33.3f) return Theme.warning();
        return Theme.danger();
    }

    @Override
    public double contentWidth(Font font) {
        return graphWidth.get();
    }

    @Override
    public double contentHeight(Font font) {
        return graphHeight.get() + (showLows.get() ? font.lineHeight + 2 : 0);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor gfx, Font font) {
        sample();

        double width = graphWidth.get();
        double height = graphHeight.get();
        double max = ceiling.get();

        Render2D.roundedRect(gfx, 0, 0, width, height, 2,
                ColorUtil.withAlpha(Theme.trackOff(), 90));

        if (showTargetLine.get()) {
            double lineY = height - (16.7 / max) * height;
            if (lineY > 0 && lineY < height) {
                Render2D.rect(gfx, 0, lineY, width, 1,
                        ColorUtil.withAlpha(Theme.textMuted(), 130));
            }
        }

        // One column per pixel, newest on the right.
        int columns = (int) width;
        for (int i = 0; i < columns && i < sampleCount; i++) {
            float millis = sampleAt(i);
            double barHeight = MathUtil.clamp(millis / max, 0.0, 1.0) * height;
            if (barHeight < 1) barHeight = 1;
            Render2D.rect(gfx, width - 1 - i, height - barHeight, 1, barHeight, barColor(millis));
        }

        if (showLows.get()) {
            float avg = average();
            float low = onePercentLow();
            String text = String.format("%.1f ms  ·  1%% low %.1f ms", avg, low);
            line(gfx, font, text, 0, height + 2, Theme.textSecondary());
        }
    }
}
