package com.gadzo.client.core.input;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rolling clicks-per-second counter for one mouse button.
 *
 * <p>Keeps the timestamp of every click inside a one-second window and reports the size of
 * that window. Sampling per tick would cap the reading at 20 and hide the fast bursts these
 * counters exist to show, so presses are recorded from the input callback instead.
 */
public class ClickTracker {

    private static final long WINDOW_MILLIS = 1000L;

    /** Bounded so a stuck autoclicker cannot grow this without limit. */
    private static final int MAX_SAMPLES = 256;

    private final Deque<Long> clicks = new ArrayDeque<>();

    private long lastClickAt;

    public synchronized void recordClick() {
        long now = System.currentTimeMillis();
        lastClickAt = now;
        if (clicks.size() >= MAX_SAMPLES) {
            clicks.pollFirst();
        }
        clicks.addLast(now);
    }

    /** Clicks within the last second. */
    public synchronized int cps() {
        prune();
        return clicks.size();
    }

    /** Milliseconds since the most recent click, for the "idle" styling of the readout. */
    public synchronized long sinceLastClick() {
        return lastClickAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - lastClickAt;
    }

    private void prune() {
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.pollFirst();
        }
    }
}
