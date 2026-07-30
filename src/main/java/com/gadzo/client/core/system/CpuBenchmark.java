package com.gadzo.client.core.system;

import com.gadzo.client.GadzoClient;

/**
 * A short single-thread CPU benchmark.
 *
 * <p>Counting cores tells you very little about Minecraft performance: the game's render and
 * tick loops are dominated by one thread, so an eight-thread part from 2012 and an
 * eight-thread part from today behave nothing alike. This measures what actually matters.
 *
 * <p>The kernel mixes floating-point and integer work behind a loop-carried dependency, so
 * the JIT can neither vectorise it nor eliminate it, and the result reflects single-thread
 * latency rather than throughput. It runs once, on a background thread, and takes well under
 * a second.
 */
public final class CpuBenchmark {

    /**
     * Reference score measured on a 2.8 GHz server-class Xeon.
     *
     * <p>The thresholds below are anchored on that single measured point and are therefore
     * coarse — they are meant to separate "fast modern core" from "decade-old core", not to
     * rank neighbouring CPUs. Anyone unhappy with the result can pick a preset by hand.
     */
    public static final int REFERENCE_SCORE = 6300;

    private static final int WARMUP_ROUNDS = 6;
    private static final int WARMUP_ITERATIONS = 300_000;
    private static final int MEASURE_ROUNDS = 5;
    private static final int MEASURE_ITERATIONS = 2_000_000;

    /** -1 until the benchmark has finished. */
    private static volatile int score = -1;

    private static volatile boolean started;

    private CpuBenchmark() {
    }

    /** Whether a score is available yet. */
    public static boolean isReady() {
        return score > 0;
    }

    /** The measured score, or -1 while it is still running. */
    public static int score() {
        return score;
    }

    /**
     * Runs the benchmark once, on a daemon thread.
     *
     * <p>Off the main thread so startup is not delayed, and as a daemon so a client shutting
     * down mid-measurement is not held open by it.
     */
    public static synchronized void startAsync() {
        if (started) {
            return;
        }
        started = true;

        Thread thread = new Thread(() -> {
            try {
                score = measure();
                GadzoClient.LOGGER.info("CPU single-thread score: {} (reference {})",
                        score, REFERENCE_SCORE);
            } catch (Exception e) {
                GadzoClient.LOGGER.warn("CPU benchmark failed; falling back to core count", e);
                score = -1;
            }
        }, "GADZO-cpu-benchmark");

        thread.setDaemon(true);
        // Below normal so the measurement never competes with game startup for a core.
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static int measure() {
        long sink = 0;
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            sink ^= kernel(WARMUP_ITERATIONS);
        }

        long best = Long.MAX_VALUE;
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            sink ^= kernel(MEASURE_ITERATIONS);
            best = Math.min(best, System.nanoTime() - start);
        }

        // Consume the accumulator so nothing above can be optimised out.
        if (sink == 42L) {
            GadzoClient.LOGGER.trace("benchmark sink");
        }

        double millis = best / 1e6;
        return (int) Math.round(100_000.0 / millis);
    }

    /**
     * The measured work itself.
     *
     * <p>Each iteration depends on the previous one through {@code acc} and {@code bits},
     * which is what forces the CPU to actually execute the chain rather than overlap it.
     */
    private static long kernel(int iterations) {
        double acc = 1.0;
        long bits = 0x9E3779B97F4A7C15L;
        for (int i = 1; i <= iterations; i++) {
            acc += Math.sqrt(i) / (acc + 1.0);
            bits ^= bits << 13;
            bits ^= bits >>> 7;
            bits ^= bits << 17;
            acc += (bits & 0xFF) * 1e-9;
        }
        return Double.doubleToLongBits(acc) ^ bits;
    }
}
