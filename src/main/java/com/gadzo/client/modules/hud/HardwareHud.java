package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.system.CpuBenchmark;
import com.gadzo.client.core.system.SystemProfile;
import com.gadzo.client.ui.Theme;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Detected hardware and the client's capability tier.
 *
 * <p>Minecraft 1.20.1 has no GPU timing API, so unlike the 26.2 branch this cannot say
 * whether a frame is CPU- or GPU-bound. That readout is left out rather than faked. What
 * 1.20.1 does expose, and later versions do not, is a real CPU model string — so the
 * processor can be named rather than merely counted.
 */
public class HardwareHud extends HudModule {

    private final BooleanSetting showGpuName;
    private final BooleanSetting showCpuName;
    private final BooleanSetting showBenchmark;
    private final BooleanSetting showHeapAdvice;

    public HardwareHud() {
        super("Hardware", "Detected GPU, CPU and capability tier", HudAnchor.BOTTOM_RIGHT, -4, -4);
        this.showGpuName = addBool("GPU name", false, "Show the graphics device model");
        this.showCpuName = addBool("CPU name", false, "Show the processor model");
        this.showBenchmark = addBool("Benchmark score", false,
                "Single-thread score against the reference machine");
        this.showHeapAdvice = addBool("Heap advice", true,
                "Warn when the memory allocation looks wrong");
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(5);
        if (showGpuName.get()) {
            lines.add(SystemProfile.gpuName());
        }
        if (showCpuName.get()) {
            lines.add(SystemProfile.cpuName());
        }
        lines.add(SystemProfile.detectTier() + " tier  ·  " + SystemProfile.cpuThreads() + "T");
        if (showBenchmark.get()) {
            lines.add(CpuBenchmark.isReady()
                    ? "CPU " + CpuBenchmark.score() + " / " + CpuBenchmark.REFERENCE_SCORE
                    : "CPU measuring...");
        }
        if (showHeapAdvice.get()) {
            String advice = SystemProfile.heapAdvice();
            if (advice != null) {
                lines.add(advice);
            }
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
        return lines().size() * font.fontHeight;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        List<String> text = lines();
        double y = 0;
        for (int i = 0; i < text.size(); i++) {
            // The tier line is the headline; a heap warning is the only thing worth alarming on.
            boolean warning = text.get(i).startsWith("Heap");
            line(gfx, font, text.get(i), 0, y,
                    warning ? Theme.warning() : (i == 0 ? Theme.accent() : Theme.textPrimary()));
            y += font.fontHeight;
        }
    }
}
