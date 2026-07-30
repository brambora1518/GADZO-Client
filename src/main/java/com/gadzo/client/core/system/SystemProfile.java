package com.gadzo.client.core.system;

import com.mojang.blaze3d.platform.GlDebugInfo;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the client can learn about the machine it is running on.
 *
 * <p>Minecraft 1.20.1 exposes less than later versions: there is no GPU device record and no
 * GPU timing, so this branch cannot report whether a frame is CPU- or GPU-bound the way the
 * 26.2 branch does. Rather than fake a verdict, that readout is simply absent here.
 *
 * <p>What 1.20.1 does offer that later versions do not is {@link GlDebugInfo#getCpuInfo()},
 * a real CPU model string, so the hardware readout can name the processor instead of only
 * counting its threads.
 */
public final class SystemProfile {

    /** Coarse capability bucket used to pick a starting preset. */
    public enum Tier {
        LOW("Low"), MEDIUM("Medium"), HIGH("High"), ULTRA("Ultra");

        private final String label;

        Tier(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Matches a VRAM size embedded in a GPU name, e.g. "GTX 1060 6GB". */
    private static final Pattern VRAM_IN_NAME = Pattern.compile("(\\d{1,3})\\s?GB", Pattern.CASE_INSENSITIVE);

    private static Tier cachedTier;

    private SystemProfile() {
    }

    public static String gpuName() {
        String name = GlDebugInfo.getRenderer();
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    public static String gpuVendor() {
        String vendor = GlDebugInfo.getVendor();
        return vendor == null || vendor.isBlank() ? "Unknown" : vendor;
    }

    /** CPU model string, which 1.20.1 exposes directly. */
    public static String cpuName() {
        String cpu = GlDebugInfo.getCpuInfo();
        return cpu == null || cpu.isBlank() ? "Unknown" : cpu;
    }

    /**
     * Whether the GPU looks like a discrete card.
     *
     * <p>A heuristic on the driver's renderer string — 1.20.1 has no device-type query. Intel
     * integrated parts name themselves clearly, and everything else is assumed discrete,
     * which errs towards the common case for a machine running modded Minecraft.
     */
    public static boolean isDiscreteGpu() {
        String name = (gpuName() + " " + gpuVendor()).toLowerCase(Locale.ROOT);
        if (name.contains("intel") && (name.contains("uhd") || name.contains("hd graphics")
                || name.contains("iris"))) {
            return false;
        }
        if (name.contains("llvmpipe") || name.contains("softpipe") || name.contains("swiftshader")) {
            return false;
        }
        return name.contains("nvidia") || name.contains("geforce") || name.contains("radeon")
                || name.contains("amd") || name.contains("arc");
    }

    /** VRAM in gigabytes if the driver spelled it out in the device name, otherwise -1. */
    public static int vramHintGb() {
        Matcher matcher = VRAM_IN_NAME.matcher(gpuName());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    public static int cpuThreads() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static long maxHeapMb() {
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    /** Total physical RAM in megabytes, or -1 when the platform will not report it. */
    public static long physicalRamMb() {
        try {
            java.lang.management.OperatingSystemMXBean bean =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                return sunBean.getTotalMemorySize() / (1024L * 1024L);
            }
        } catch (Throwable ignored) {
            // Not available on this JVM; the advisory skips the ratio check.
        }
        return -1;
    }

    /**
     * Capability bucket, dominated by measured single-thread CPU speed.
     *
     * <p>Core count says very little about Minecraft performance — the render and tick loops
     * are single-thread bound, so an eight-thread part from 2012 and one from today behave
     * nothing alike. Heap size is deliberately excluded: it is a launcher setting the player
     * chose, and a large value is as often a misconfiguration as a sign of a capable machine.
     */
    public static Tier detectTier() {
        if (cachedTier != null) {
            return cachedTier;
        }
        if (!CpuBenchmark.isReady()) {
            return Tier.MEDIUM;
        }

        int score = 0;
        int cpu = CpuBenchmark.score();
        int reference = CpuBenchmark.REFERENCE_SCORE;
        if (cpu >= reference * 1.15) {
            score += 4;
        } else if (cpu >= reference * 0.85) {
            score += 3;
        } else if (cpu >= reference * 0.60) {
            score += 2;
        } else if (cpu >= reference * 0.40) {
            score += 1;
        }

        int threads = cpuThreads();
        if (threads >= 12) {
            score += 1;
        } else if (threads <= 3) {
            score -= 1;
        }
        if (isDiscreteGpu()) {
            score += 1;
        }
        if (vramHintGb() >= 6) {
            score += 1;
        }

        cachedTier = score >= 7 ? Tier.ULTRA
                : score >= 5 ? Tier.HIGH
                : score >= 3 ? Tier.MEDIUM
                : Tier.LOW;
        return cachedTier;
    }

    /**
     * Advice about the heap allocation, or {@code null} when it looks sensible.
     *
     * <p>Over-allocating is the most common self-inflicted Minecraft performance problem.
     * Above roughly 4 GB the collector runs less often but for longer, which is felt as
     * periodic stutter rather than a lower average frame rate, and a very large heap squeezes
     * the OS page cache that chunk loading depends on.
     *
     * <p>A modpack the size of Create genuinely wants more than vanilla, so the ceiling here
     * is higher than on the 26.2 branch.
     */
    public static String heapAdvice() {
        long heap = maxHeapMb();
        long physical = physicalRamMb();

        if (physical > 0 && heap > physical * 0.55) {
            return "Heap is " + heap + " MB of " + physical + " MB system RAM — leave more for the OS";
        }
        if (heap >= 8000) {
            return "Heap is " + heap + " MB; even with Create, 4-6 GB usually stutters less";
        }
        if (heap < 2000) {
            return "Heap is only " + heap + " MB — Create wants about 4 GB";
        }
        return null;
    }

    public static String summary() {
        return gpuName() + "  ·  " + cpuThreads() + " threads  ·  " + maxHeapMb() + " MB heap";
    }
}
