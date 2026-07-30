package com.gadzo.client.core.system;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the client can learn about the machine it is running on.
 *
 * <p>Used for two things: picking a sensible starting preset, and telling the player which
 * side of the pipeline is actually limiting their frame rate.
 *
 * <p>The second matters more than it sounds. Minecraft is overwhelmingly CPU- and draw-call-
 * bound rather than shader-bound, so the folk wisdom of turning texture quality down is
 * usually wasted effort — and on a card with plenty of VRAM it is actively counterproductive,
 * because reduced mipmaps mean more texture cache misses at distance. Measuring the split
 * lets the client recommend the lever that will actually move.
 */
public final class SystemProfile {

    /** Coarse capability bucket used to pick a starting preset. */
    public enum Tier {
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High"),
        ULTRA("Ultra");

        private final String label;

        Tier(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Which side of the pipeline is limiting the frame rate right now. */
    public enum Bottleneck {
        GPU_BOUND("GPU bound", "Lower render distance, resolution or shader load"),
        CPU_BOUND("CPU bound", "Lower entity count, particles and simulation distance"),
        BALANCED("Balanced", "Neither side is clearly limiting"),
        UNKNOWN("Measuring...", "Not enough samples yet"),
        NO_TIMING("GPU timing off", "Enable GPU timing in this module's settings");

        private final String label;
        private final String advice;

        Bottleneck(String label, String advice) {
            this.label = label;
            this.advice = advice;
        }

        public String label() {
            return label;
        }

        public String advice() {
            return advice;
        }
    }

    /** Matches a VRAM size embedded in a GPU name, e.g. "GTX 1060 6GB". */
    private static final Pattern VRAM_IN_NAME = Pattern.compile("(\\d{1,3})\\s?GB", Pattern.CASE_INSENSITIVE);

    /** GPU utilisation at or above this counts as saturated. */
    private static final double GPU_SATURATED = 88.0;

    /** GPU utilisation at or below this, while frames are slow, means the CPU is the limit. */
    private static final double GPU_IDLE = 62.0;

    /** Below this frame rate the client is slow enough to be worth diagnosing. */
    private static final int SLOW_FPS = 100;

    private static Tier cachedTier;

    private SystemProfile() {
    }

    // -- raw facts ----------------------------------------------------------------------

    private static DeviceInfo deviceInfo() {
        GpuDevice device = RenderSystem.tryGetDevice();
        return device == null ? null : device.getDeviceInfo();
    }

    /** GPU model string as reported by the driver, or "Unknown" before the device exists. */
    public static String gpuName() {
        DeviceInfo info = deviceInfo();
        return info == null ? "Unknown" : info.name();
    }

    public static String gpuVendor() {
        DeviceInfo info = deviceInfo();
        return info == null ? "Unknown" : info.vendorName();
    }

    public static String backendName() {
        DeviceInfo info = deviceInfo();
        return info == null ? "Unknown" : info.backendName();
    }

    public static boolean isDiscreteGpu() {
        DeviceInfo info = deviceInfo();
        return info != null && info.type() == DeviceType.DISCRETE;
    }

    /**
     * VRAM in gigabytes if the driver spelled it out in the device name, otherwise -1.
     *
     * <p>There is no portable VRAM query in the render backend, and the name is the only
     * place the figure reliably appears. Treated as a hint, never as a hard input.
     */
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

    /** Largest texture the GPU will accept; a decent proxy for how modern it is. */
    public static int maxTextureSize() {
        DeviceInfo info = deviceInfo();
        return info == null ? 0 : info.limits().maxTextureSize();
    }

    // -- derived ------------------------------------------------------------------------

    /**
     * Capability bucket, derived from measurable facts rather than a model-name lookup.
     *
     * <p>A name table would need constant maintenance and would be wrong for anything it had
     * not seen. Discrete-vs-integrated, thread count, heap size and maximum texture size are
     * all queryable and correlate well enough to pick a starting point the player can then
     * override.
     */
    public static Tier detectTier() {
        if (cachedTier != null) {
            return cachedTier;
        }
        if (deviceInfo() == null) {
            // Device not created yet — do not cache a guess made too early.
            return Tier.MEDIUM;
        }

        if (!CpuBenchmark.isReady()) {
            // Without the CPU measurement any verdict would be dominated by GPU signals,
            // which is precisely the mistake this method exists to avoid. Wait for it.
            return Tier.MEDIUM;
        }

        int score = 0;

        // Single-thread CPU speed carries the most weight, because it is what actually gates
        // Minecraft's frame rate. A fast GPU behind a slow core is still a slow client.
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

        // More threads help chunk meshing, but only secondarily.
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
        if (maxTextureSize() >= 16384) {
            score += 1;
        }

        // Heap size is deliberately absent. It is a launcher setting the player chose, not a
        // property of the machine, and a large one is as often a misconfiguration as a sign
        // of a capable system — see heapAdvice().

        cachedTier = score >= 7 ? Tier.ULTRA
                : score >= 5 ? Tier.HIGH
                : score >= 3 ? Tier.MEDIUM
                : Tier.LOW;
        return cachedTier;
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
            // Not available on this JVM; the advisory simply skips the ratio check.
        }
        return -1;
    }

    /**
     * Advice about the heap allocation, or {@code null} when it looks sensible.
     *
     * <p>Over-allocating is the most common self-inflicted Minecraft performance problem.
     * Vanilla with a normal render distance works comfortably in 2–4 GB; past that the
     * garbage collector simply has a larger young generation to sweep, so collections happen
     * less often but each one takes longer — which is felt as periodic stutter rather than a
     * lower average frame rate. A very large heap also squeezes the OS page cache, which is
     * what makes chunk loading feel slow.
     */
    public static String heapAdvice() {
        long heap = maxHeapMb();
        long physical = physicalRamMb();

        if (physical > 0 && heap > physical * 0.55) {
            return "Heap is " + heap + " MB of " + physical + " MB system RAM — leave more for the OS";
        }
        if (heap >= 6000) {
            return "Heap is " + heap + " MB; 3–4 GB usually stutters less (longer GC pauses above that)";
        }
        if (heap < 1500) {
            return "Heap is only " + heap + " MB — raise it to about 3 GB";
        }
        return null;
    }

    /**
     * GPU load as a percentage, as sampled by the game's own profiler.
     *
     * <p>Only populated while the vanilla {@code GPU_UTILIZATION} debug entry is active —
     * {@code Minecraft.renderFrame} zeroes this field otherwise rather than paying for a
     * timer query every frame. {@link #isGpuTimingActive()} reports whether the reading is
     * live, so callers can say "off" instead of showing a misleading 0%.
     */
    public static double gpuUtilization() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? 0.0 : client.getGpuUtilization();
    }

    /** Whether the game is currently running GPU timer queries. */
    public static boolean isGpuTimingActive() {
        Minecraft client = Minecraft.getInstance();
        return client != null
                && client.debugEntries.isCurrentlyEnabled(DebugScreenEntries.GPU_UTILIZATION);
    }

    /**
     * Which side is limiting the frame rate.
     *
     * <p>The logic is deliberately conservative: a verdict is only given when the frame rate
     * is low enough to be worth acting on. A client running comfortably fast is reported as
     * balanced rather than being told to change something.
     */
    public static Bottleneck bottleneck() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return Bottleneck.UNKNOWN;
        }

        if (!isGpuTimingActive()) {
            return Bottleneck.NO_TIMING;
        }

        int fps = client.getFps();
        double gpu = gpuUtilization();

        // No meaningful sample yet: the profiler reports zero until it has timed a frame.
        if (gpu <= 0.0) {
            return Bottleneck.UNKNOWN;
        }
        if (fps >= SLOW_FPS) {
            return Bottleneck.BALANCED;
        }
        if (gpu >= GPU_SATURATED) {
            return Bottleneck.GPU_BOUND;
        }
        if (gpu <= GPU_IDLE) {
            // Frames are slow while the GPU is idling — the frame time is being spent on the
            // CPU, in chunk meshing, entity handling or draw-call submission.
            return Bottleneck.CPU_BOUND;
        }
        return Bottleneck.BALANCED;
    }

    /** One-line hardware summary for the menu footer. */
    public static String summary() {
        return gpuName() + "  ·  " + cpuThreads() + " threads  ·  " + maxHeapMb() + " MB heap";
    }
}
