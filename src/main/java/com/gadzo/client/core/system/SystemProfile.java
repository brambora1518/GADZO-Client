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

        int score = 0;

        if (isDiscreteGpu()) {
            score += 2;
        }

        int threads = cpuThreads();
        if (threads >= 16) {
            score += 2;
        } else if (threads >= 8) {
            score += 1;
        } else if (threads <= 4) {
            score -= 1;
        }

        long heap = maxHeapMb();
        if (heap >= 6000) {
            score += 2;
        } else if (heap >= 3000) {
            score += 1;
        } else if (heap < 2000) {
            score -= 1;
        }

        int vram = vramHintGb();
        if (vram >= 8) {
            score += 2;
        } else if (vram >= 6) {
            score += 1;
        }

        if (maxTextureSize() >= 16384) {
            score += 1;
        }

        cachedTier = score >= 7 ? Tier.ULTRA
                : score >= 5 ? Tier.HIGH
                : score >= 2 ? Tier.MEDIUM
                : Tier.LOW;
        return cachedTier;
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
