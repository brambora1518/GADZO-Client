package com.gadzo.client.core.system;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Measures how evenly the game is running, and says why when it is not.
 *
 * <p>Average frame rate is close to useless for diagnosing stutter. A client that renders 200
 * frames in 900 ms and then one frame in 100 ms reports 201 FPS and feels terrible. What the
 * player notices is the long frames, so this records every frame time and reports the
 * <em>lows</em> — the mean frame rate across the worst 1% and 0.1% of frames.
 *
 * <p>The more useful half is attribution. Each frame the accumulated pause time reported by
 * every {@link GarbageCollectorMXBean} is sampled, and a long frame that coincides with the
 * collector running is recorded as a GC pause rather than as an anonymous spike. That is what
 * turns "it stutters" into "it stutters because the heap is too big", which is a problem
 * someone can actually go and fix.
 *
 * <p>Everything here runs on the render thread, once per frame. The per-frame cost is two
 * counter reads and a ring-buffer write; the statistics — which need a sort — are recomputed
 * at most every {@value #RECOMPUTE_MILLIS} ms, because a monitor that caused stutter while
 * measuring it would be a poor joke.
 */
public final class FrameTimeMonitor {

    /** What a long frame was blamed on. */
    public enum Cause {
        /** The collector ran during this frame. */
        GC("GC pause"),
        /** No collector activity — chunk meshing, a shader compile, or the OS. */
        OTHER("Render spike");

        private final String label;

        Cause(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** One recorded stutter. */
    public record Spike(long atMillis, double durationMs, Cause cause) {
    }

    /** Frames kept for the percentile lows — about ten seconds at 60 FPS. */
    private static final int CAPACITY = 600;

    /** How long spikes and GC pauses stay in the rolling window. */
    private static final long WINDOW_MILLIS = 60_000L;

    /** Cap on remembered spikes, so a pathological session cannot grow the deque forever. */
    private static final int SPIKE_LIMIT = 128;

    /**
     * A frame must exceed both of these to count as a stutter: an absolute floor, so a machine
     * running at 300 FPS does not report every 6 ms frame as a spike, and a multiple of the
     * typical frame, so a machine running at 40 FPS is not permanently in alarm.
     */
    private static final double MIN_SPIKE_MS = 18.0;
    private static final double SPIKE_FACTOR = 2.5;

    /** Collector time within one frame that is enough to blame the frame on it. */
    private static final long GC_ATTRIBUTION_MS = 2L;

    /**
     * Frames longer than this are a loading screen, a world join or an alt-tab, not stutter.
     * Recording them would poison the percentiles for the next ten seconds of real play.
     */
    private static final double DISCARD_ABOVE_MS = 2_000.0;

    private static final long RECOMPUTE_MILLIS = 500L;

    private static final double[] frames = new double[CAPACITY];
    private static int count;
    private static int cursor;

    private static long lastFrameNanos;
    private static long lastGcMillis = -1L;

    private static final Deque<Spike> spikes = new ArrayDeque<>();
    private static final Deque<long[]> gcPauses = new ArrayDeque<>();

    // Cached statistics, all recomputed together.
    private static long lastRecomputeAt;
    private static double cachedMedian = 16.7;
    private static double cachedAverageMs = 16.7;
    private static double cachedLowOnePercent;
    private static double cachedLowTenthPercent;

    private FrameTimeMonitor() {
    }

    // -- sampling ---------------------------------------------------------------------

    /** Records one frame. Called from the tail of the client's render method. */
    public static void sample() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            lastGcMillis = collectorMillis();
            return;
        }

        double elapsedMs = (now - lastFrameNanos) / 1_000_000.0;
        lastFrameNanos = now;

        long gcNow = collectorMillis();
        long gcDelta = lastGcMillis < 0 ? 0 : Math.max(0L, gcNow - lastGcMillis);
        lastGcMillis = gcNow;

        long wallClock = System.currentTimeMillis();
        evictOlderThan(wallClock - WINDOW_MILLIS);

        if (gcDelta > 0) {
            gcPauses.addLast(new long[] { wallClock, gcDelta });
        }

        if (elapsedMs > DISCARD_ABOVE_MS) {
            // Treat a loading pause as a discontinuity rather than as data.
            reset();
            return;
        }

        frames[cursor] = elapsedMs;
        cursor = (cursor + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }

        recomputeIfDue(wallClock);

        if (count >= 60 && elapsedMs >= MIN_SPIKE_MS && elapsedMs >= cachedMedian * SPIKE_FACTOR) {
            Cause cause = gcDelta >= GC_ATTRIBUTION_MS ? Cause.GC : Cause.OTHER;
            spikes.addLast(new Spike(wallClock, elapsedMs, cause));
            while (spikes.size() > SPIKE_LIMIT) {
                spikes.removeFirst();
            }
        }
    }

    /**
     * Clears the frame history.
     *
     * <p>The rolling spike and GC windows are deliberately kept: they are what a warning is
     * built from, and a world change is exactly when a heap problem tends to show itself.
     */
    public static void reset() {
        count = 0;
        cursor = 0;
        lastRecomputeAt = 0L;
    }

    private static void evictOlderThan(long cutoff) {
        while (!spikes.isEmpty() && spikes.peekFirst().atMillis() < cutoff) {
            spikes.removeFirst();
        }
        while (!gcPauses.isEmpty() && gcPauses.peekFirst()[0] < cutoff) {
            gcPauses.removeFirst();
        }
    }

    private static long collectorMillis() {
        long total = 0L;
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        for (int i = 0; i < beans.size(); i++) {
            long time = beans.get(i).getCollectionTime();
            if (time > 0) {
                total += time;
            }
        }
        return total;
    }

    // -- statistics -------------------------------------------------------------------

    private static void recomputeIfDue(long now) {
        if (now - lastRecomputeAt < RECOMPUTE_MILLIS || count == 0) {
            return;
        }
        lastRecomputeAt = now;

        double[] sorted = Arrays.copyOf(frames, count);
        Arrays.sort(sorted);

        cachedMedian = sorted[sorted.length / 2];

        double sum = 0.0;
        for (double value : sorted) {
            sum += value;
        }
        cachedAverageMs = sum / sorted.length;

        cachedLowOnePercent = meanOfWorst(sorted, 0.01);
        cachedLowTenthPercent = meanOfWorst(sorted, 0.001);
    }

    /**
     * Mean frame rate across the slowest {@code fraction} of frames.
     *
     * <p>Averaging the tail rather than taking the single percentile value is what makes the
     * number stable enough to watch: one unlucky frame moves an average of six much less than
     * it moves a lone sample.
     */
    private static double meanOfWorst(double[] ascending, double fraction) {
        int take = Math.max(1, (int) Math.round(ascending.length * fraction));
        double sum = 0.0;
        for (int i = ascending.length - take; i < ascending.length; i++) {
            sum += ascending[i];
        }
        double meanMs = sum / take;
        return meanMs <= 0.0 ? 0.0 : 1000.0 / meanMs;
    }

    public static boolean hasData() {
        return count >= 60;
    }

    /** Mean frame rate over the recorded window. */
    public static double averageFps() {
        return cachedAverageMs <= 0.0 ? 0.0 : 1000.0 / cachedAverageMs;
    }

    /** Mean frame rate across the worst 1% of frames. */
    public static double lowOnePercent() {
        return cachedLowOnePercent;
    }

    /** Mean frame rate across the worst 0.1% of frames. */
    public static double lowTenthPercent() {
        return cachedLowTenthPercent;
    }

    public static double medianFrameMs() {
        return cachedMedian;
    }

    public static int spikesInWindow() {
        evictOlderThan(System.currentTimeMillis() - WINDOW_MILLIS);
        return spikes.size();
    }

    public static int gcSpikesInWindow() {
        evictOlderThan(System.currentTimeMillis() - WINDOW_MILLIS);
        int total = 0;
        for (Spike spike : spikes) {
            if (spike.cause() == Cause.GC) {
                total++;
            }
        }
        return total;
    }

    /** Total collector pause time in the last minute, in milliseconds. */
    public static long gcMillisInWindow() {
        evictOlderThan(System.currentTimeMillis() - WINDOW_MILLIS);
        long total = 0L;
        for (long[] pause : gcPauses) {
            total += pause[1];
        }
        return total;
    }

    /** The longest frame in the last minute, or {@code null} when there were no spikes. */
    public static Spike worstInWindow() {
        evictOlderThan(System.currentTimeMillis() - WINDOW_MILLIS);
        Spike worst = null;
        for (Spike spike : spikes) {
            if (worst == null || spike.durationMs() > worst.durationMs()) {
                worst = spike;
            }
        }
        return worst;
    }

    /** Names of the collectors in use, for the diagnosis text. */
    public static String collectorNames() {
        StringBuilder names = new StringBuilder();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (names.length() > 0) {
                names.append(" + ");
            }
            names.append(bean.getName());
        }
        return names.length() == 0 ? "unknown" : names.toString();
    }

    /**
     * Whether the collector is one of the throughput-oriented ones.
     *
     * <p>Relevant because those are the collectors whose pauses scale with heap size, which is
     * the mechanism behind "I gave Minecraft more RAM and it got choppier".
     */
    public static boolean usingStopTheWorldCollector() {
        String names = collectorNames().toLowerCase(java.util.Locale.ROOT);
        return names.contains("parallel") || names.contains("copy")
                || names.contains("marksweep") || names.contains("psscavenge");
    }
}
