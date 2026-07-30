package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.system.SystemProfile;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * GPU load and a verdict on what is limiting the frame rate.
 *
 * <p>The useful part is the verdict rather than the raw number. A player who sees "CPU bound"
 * knows that dropping texture quality will do nothing and that entity count, particles and
 * simulation distance are where the frames are; a player who sees "GPU bound" knows the
 * opposite. Without it, tuning is guesswork.
 */
public class HardwareHud extends HudModule {

    private static final double BAR_WIDTH = 70.0;
    private static final double BAR_HEIGHT = 3.0;

    private final BooleanSetting showGpuName;
    private final BooleanSetting showUtilBar;
    private final BooleanSetting showAdvice;
    private final BooleanSetting gpuTiming;

    /** The player's own debug-entry setting, restored when GPU timing is switched back off. */
    private DebugScreenEntryStatus savedGpuEntryStatus;

    public HardwareHud() {
        super("Hardware", "GPU load and frame-rate bottleneck", HudAnchor.BOTTOM_RIGHT, -4, -4);
        this.showGpuName = addBool("GPU name", false, "Show the graphics device model");
        this.gpuTiming = addBool("GPU timing", false,
                "Measure GPU load. Costs a timer query per frame and makes vanilla's debug "
                        + "overlay show its own GPU line; your previous setting is restored when "
                        + "you turn this off");
        this.showUtilBar = add(new BooleanSetting("Load bar", true)
                .<BooleanSetting>describe("Draw a bar for GPU utilisation")
                .visibleWhen(() -> this.gpuTiming.get()));
        this.showAdvice = addBool("Advice", false, "Show what to change for the current bottleneck");

        this.gpuTiming.onChange(enabled -> {
            if (isEnabled()) {
                applyGpuTiming(enabled);
            }
        });
    }

    /**
     * Turns vanilla's GPU timer query on or off.
     *
     * <p>The game only measures GPU time while its {@code GPU_UTILIZATION} debug entry is
     * active, so there is no way to read the figure without flipping that entry. Because the
     * change is persisted by vanilla, the previous value is captured and put back rather than
     * silently left switched on.
     */
    private void applyGpuTiming(boolean enabled) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        if (enabled) {
            if (savedGpuEntryStatus == null) {
                savedGpuEntryStatus = client.debugEntries.getStatus(DebugScreenEntries.GPU_UTILIZATION);
            }
            client.debugEntries.setStatus(DebugScreenEntries.GPU_UTILIZATION,
                    DebugScreenEntryStatus.ALWAYS_ON);
        } else if (savedGpuEntryStatus != null) {
            client.debugEntries.setStatus(DebugScreenEntries.GPU_UTILIZATION, savedGpuEntryStatus);
            savedGpuEntryStatus = null;
        }
    }

    @Override
    protected void onEnable() {
        if (gpuTiming.get()) {
            applyGpuTiming(true);
        }
    }

    @Override
    protected void onDisable() {
        applyGpuTiming(false);
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(4);
        if (showGpuName.get()) {
            lines.add(SystemProfile.gpuName());
        }
        if (gpuTiming.get() && SystemProfile.isGpuTimingActive()) {
            lines.add(String.format("GPU %.0f%%", SystemProfile.gpuUtilization()));
        } else {
            // Without GPU timing the tier and thread count are still worth showing, and are
            // honest about what is actually known.
            lines.add(SystemProfile.detectTier() + " tier  ·  " + SystemProfile.cpuThreads() + "T");
        }
        lines.add(SystemProfile.bottleneck().label());
        if (showAdvice.get()) {
            lines.add(SystemProfile.bottleneck().advice());
        }
        return lines;
    }

    private static int verdictColor() {
        return switch (SystemProfile.bottleneck()) {
            case GPU_BOUND -> Theme.warning();
            case CPU_BOUND -> Theme.danger();
            case BALANCED -> Theme.success();
            case UNKNOWN, NO_TIMING -> Theme.textMuted();
        };
    }

    @Override
    public double contentWidth(Font font) {
        double widest = showUtilBar.get() ? BAR_WIDTH : 0;
        for (String line : lines()) {
            widest = Math.max(widest, font.width(line));
        }
        return widest;
    }

    @Override
    public double contentHeight(Font font) {
        return lines().size() * font.lineHeight + (showUtilBar.get() ? BAR_HEIGHT + 3 : 0);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor gfx, Font font) {
        List<String> text = lines();
        double y = 0;

        for (int i = 0; i < text.size(); i++) {
            // The verdict line is the one worth colouring; the rest stay neutral.
            boolean isVerdict = text.get(i).equals(SystemProfile.bottleneck().label());
            line(gfx, font, text.get(i), 0, y, isVerdict ? verdictColor() : Theme.textPrimary());
            y += font.lineHeight;
        }

        if (showUtilBar.get()) {
            double fraction = Math.min(1.0, SystemProfile.gpuUtilization() / 100.0);
            y += 1;
            Render2D.roundedRect(gfx, 0, y, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                    ColorUtil.withAlpha(Theme.trackOff(), 180));
            if (fraction > 0.01) {
                Render2D.roundedRect(gfx, 0, y, BAR_WIDTH * fraction, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                        verdictColor());
            }
        }
    }
}
