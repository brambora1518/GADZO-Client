package com.gadzo.client.ui.notify;

import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.Easing;

/** A single toast: its content, its severity, and the animation driving its slide-in. */
public class Notification {

    /** Severity, which selects the accent stripe colour and icon glyph. */
    public enum Level {
        INFO("i"),
        SUCCESS("+"),
        WARNING("!"),
        ERROR("x");

        private final String glyph;

        Level(String glyph) {
            this.glyph = glyph;
        }

        public String glyph() {
            return glyph;
        }

        public int color() {
            return switch (this) {
                case INFO -> Theme.accent();
                case SUCCESS -> Theme.success();
                case WARNING -> Theme.warning();
                case ERROR -> Theme.danger();
            };
        }
    }

    private final String title;
    private final String message;
    private final Level level;
    private final long durationMillis;
    private final long createdAt;

    /** 0 fully off-screen, 1 fully shown. */
    private final Animation slide = new Animation(0.0, 260L, Easing.EXPO_OUT);

    /** Vertical position in the stack, animated so toasts slide up as others expire. */
    private final Animation stackOffset = new Animation(0.0, 220L, Easing.CUBIC_OUT);

    private boolean dismissing;

    public Notification(String title, String message, Level level, long durationMillis) {
        this.title = title;
        this.message = message;
        this.level = level;
        this.durationMillis = durationMillis;
        this.createdAt = System.currentTimeMillis();
        this.slide.to(1.0);
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public Level level() {
        return level;
    }

    public Animation stackOffset() {
        return stackOffset;
    }

    public double slideProgress() {
        return slide.value();
    }

    /** Fraction of the lifetime elapsed, used to draw the countdown bar. */
    public double lifeProgress() {
        return Math.min(1.0, (System.currentTimeMillis() - createdAt) / (double) durationMillis);
    }

    public void beginDismiss() {
        if (!dismissing) {
            dismissing = true;
            slide.to(0.0);
        }
    }

    public boolean isDismissing() {
        return dismissing;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt >= durationMillis;
    }

    /** True once the slide-out has finished and the toast can be dropped. */
    public boolean isFinished() {
        return dismissing && slide.isFinished() && slide.value() <= 0.001;
    }
}
