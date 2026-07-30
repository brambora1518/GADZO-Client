package com.gadzo.client.core.input;

/**
 * Client-wide input state that HUD elements read from.
 *
 * <p>Fed by mixins on the mouse and keyboard callbacks, so readings reflect real input
 * events rather than a 20 Hz tick sample.
 */
public final class InputTracker {

    public static final int BUTTON_LEFT = 0;
    public static final int BUTTON_RIGHT = 1;

    private static final ClickTracker LEFT = new ClickTracker();
    private static final ClickTracker RIGHT = new ClickTracker();

    private InputTracker() {
    }

    /** Called from the mouse-button mixin on press. */
    public static void onMousePress(int button) {
        switch (button) {
            case BUTTON_LEFT -> LEFT.recordClick();
            case BUTTON_RIGHT -> RIGHT.recordClick();
            default -> {
                // Side buttons are not tracked.
            }
        }
    }

    public static int leftCps() {
        return LEFT.cps();
    }

    public static int rightCps() {
        return RIGHT.cps();
    }

    public static ClickTracker left() {
        return LEFT;
    }

    public static ClickTracker right() {
        return RIGHT;
    }
}
