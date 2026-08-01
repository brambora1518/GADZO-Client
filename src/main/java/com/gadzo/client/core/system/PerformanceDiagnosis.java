package com.gadzo.client.core.system;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what {@link FrameTimeMonitor} measured into things a player can act on.
 *
 * <p>Every finding here is derived from a measurement taken on this machine, in this session.
 * Nothing is inferred from the mod list or guessed from the hardware: a client that announces
 * "your heap is too big" to someone whose game runs perfectly has taught them to ignore it,
 * and the one time it matters they will.
 *
 * <p>The most common finding by a wide margin is an over-allocated heap. Handing Minecraft
 * most of the system's RAM is folk advice that survives because it sounds obviously right, and
 * it does raise the average frame rate slightly while making the game feel worse: a larger heap
 * is collected less often but for longer, and every collection is a frame that does not arrive.
 * That is felt as periodic hitching, which is exactly the complaint it is offered as a cure for.
 */
public final class PerformanceDiagnosis {

    /** How much a finding matters. */
    public enum Severity {
        GOOD, INFO, WARN, BAD
    }

    /**
     * One conclusion.
     *
     * @param title  short enough for a notification headline
     * @param detail one sentence saying what to change
     */
    public record Finding(Severity severity, String title, String detail) {
    }

    /** Below this the frame rate is unpleasant regardless of what the average says. */
    private static final double POOR_LOW_FPS = 30.0;

    /** A 1% low this far under the average means the problem is pacing, not raw speed. */
    private static final double PACING_RATIO = 0.45;

    /** Spikes per minute at which stutter stops being occasional. */
    private static final int NOTICEABLE_SPIKES = 6;

    /** Collector time per minute, in ms, that is enough to be the main story. */
    private static final long HEAVY_GC_MILLIS = 1_500L;

    private PerformanceDiagnosis() {
    }

    /**
     * The heap size this machine should probably be using, in megabytes.
     *
     * <p>Create genuinely needs more than vanilla — 4 GB is the floor for a pack of that size —
     * but the gain stops well before the numbers people usually pick. The cap of 6 GB and the
     * 40% share of physical RAM both exist to leave the OS enough page cache for chunk loading,
     * which is what actually feeds the game data.
     */
    public static long recommendedHeapMb() {
        long physical = SystemProfile.physicalRamMb();
        long ceiling = 6144L;
        if (physical > 0) {
            ceiling = Math.min(ceiling, Math.round(physical * 0.40));
        }
        return Math.max(2048L, ceiling);
    }

    /** Every finding, worst first. */
    public static List<Finding> run() {
        List<Finding> findings = new ArrayList<>();

        long heap = SystemProfile.maxHeapMb();
        long physical = SystemProfile.physicalRamMb();
        long recommended = recommendedHeapMb();

        int spikes = FrameTimeMonitor.spikesInWindow();
        int gcSpikes = FrameTimeMonitor.gcSpikesInWindow();
        long gcMillis = FrameTimeMonitor.gcMillisInWindow();

        // -- collector pauses, measured ------------------------------------------------
        if (gcSpikes >= 2 && gcSpikes * 2 >= spikes) {
            String detail = gcSpikes + " of the last " + spikes + " stutters landed on a "
                    + "garbage collection";
            if (heap > recommended + 512) {
                detail += "; try " + recommended + " MB instead of " + heap + " MB";
            }
            findings.add(new Finding(Severity.BAD, "Stutter is garbage collection", detail));
        } else if (gcMillis >= HEAVY_GC_MILLIS) {
            findings.add(new Finding(Severity.WARN, "Collector is busy",
                    gcMillis + " ms of the last minute was spent collecting ("
                            + FrameTimeMonitor.collectorNames() + ")"));
        }

        // -- heap sizing, whether or not it has bitten yet -----------------------------
        if (physical > 0 && heap > physical * 0.55) {
            findings.add(new Finding(Severity.BAD, "Heap is too large for this machine",
                    heap + " MB of " + physical + " MB leaves too little for the OS and chunk "
                            + "cache — " + recommended + " MB suits this system better"));
        } else if (heap > recommended + 1024) {
            findings.add(new Finding(Severity.WARN, "Heap is larger than it needs to be",
                    heap + " MB collects less often but for longer; " + recommended
                            + " MB is smoother for a Create pack"));
        } else if (heap < 3072) {
            findings.add(new Finding(Severity.WARN, "Heap is small for Create",
                    heap + " MB will collect constantly under Create — " + recommended
                            + " MB is a better fit"));
        }

        if (FrameTimeMonitor.usingStopTheWorldCollector() && heap >= 4096) {
            findings.add(new Finding(Severity.INFO, "Old collector on a large heap",
                    "Running " + FrameTimeMonitor.collectorNames() + "; -XX:+UseG1GC keeps "
                            + "pauses short as the heap grows"));
        }

        // -- pacing, measured ----------------------------------------------------------
        if (FrameTimeMonitor.hasData()) {
            double average = FrameTimeMonitor.averageFps();
            double low = FrameTimeMonitor.lowOnePercent();

            if (low > 0 && average > 0 && low < average * PACING_RATIO && spikes >= NOTICEABLE_SPIKES) {
                findings.add(new Finding(Severity.WARN, "Frame pacing is uneven",
                        "Average " + Math.round(average) + " FPS but a 1% low of "
                                + Math.round(low) + " — the long frames are what you feel"));
            }
            if (low > 0 && low < POOR_LOW_FPS && spikes < NOTICEABLE_SPIKES) {
                findings.add(new Finding(Severity.WARN, "Frame rate is low but steady",
                        "1% low of " + Math.round(low) + " FPS with little stutter — this is "
                                + "render load, so lower render distance or shadow quality"));
            }
            if (spikes >= NOTICEABLE_SPIKES && gcSpikes * 2 < spikes) {
                findings.add(new Finding(Severity.WARN, "Stutter is not the collector",
                        spikes + " stutters a minute with the collector mostly idle — usually "
                                + "chunk building; lower render distance or cap the frame rate"));
            }
        }

        if (findings.isEmpty()) {
            findings.add(new Finding(Severity.GOOD, "Nothing obviously wrong",
                    FrameTimeMonitor.hasData()
                            ? "1% low of " + Math.round(FrameTimeMonitor.lowOnePercent())
                                    + " FPS with " + spikes + " stutters in the last minute"
                            : "Still measuring — play for a minute and check again"));
        }

        findings.sort((a, b) -> b.severity().compareTo(a.severity()));
        return findings;
    }

    /** The single most important finding, for a one-line readout. */
    public static Finding headline() {
        return run().get(0);
    }
}
