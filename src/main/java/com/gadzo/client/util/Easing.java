package com.gadzo.client.util;

/**
 * Easing curves used by every animated surface in the client.
 *
 * <p>All functions map a normalised progress {@code t} in {@code [0, 1]} onto an eased
 * value. {@link #EXPO_OUT} is the house default: it moves fast off the mark and settles
 * gently, which is what makes menu transitions feel responsive rather than floaty.
 */
public enum Easing {
    LINEAR("Linear") {
        @Override
        public double apply(double t) {
            return t;
        }
    },
    SINE_OUT("Sine Out") {
        @Override
        public double apply(double t) {
            return Math.sin((t * Math.PI) / 2.0);
        }
    },
    SINE_IN_OUT("Sine In Out") {
        @Override
        public double apply(double t) {
            return -(Math.cos(Math.PI * t) - 1.0) / 2.0;
        }
    },
    QUAD_OUT("Quad Out") {
        @Override
        public double apply(double t) {
            return 1.0 - (1.0 - t) * (1.0 - t);
        }
    },
    CUBIC_OUT("Cubic Out") {
        @Override
        public double apply(double t) {
            double inv = 1.0 - t;
            return 1.0 - inv * inv * inv;
        }
    },
    CUBIC_IN_OUT("Cubic In Out") {
        @Override
        public double apply(double t) {
            return t < 0.5
                    ? 4.0 * t * t * t
                    : 1.0 - Math.pow(-2.0 * t + 2.0, 3) / 2.0;
        }
    },
    EXPO_OUT("Expo Out") {
        @Override
        public double apply(double t) {
            return t >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
        }
    },
    EXPO_IN_OUT("Expo In Out") {
        @Override
        public double apply(double t) {
            if (t <= 0.0) return 0.0;
            if (t >= 1.0) return 1.0;
            return t < 0.5
                    ? Math.pow(2.0, 20.0 * t - 10.0) / 2.0
                    : (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0;
        }
    },
    BACK_OUT("Back Out") {
        @Override
        public double apply(double t) {
            final double c1 = 1.70158;
            final double c3 = c1 + 1.0;
            double inv = t - 1.0;
            return 1.0 + c3 * inv * inv * inv + c1 * inv * inv;
        }
    },
    ELASTIC_OUT("Elastic Out") {
        @Override
        public double apply(double t) {
            if (t <= 0.0) return 0.0;
            if (t >= 1.0) return 1.0;
            final double c4 = (2.0 * Math.PI) / 3.0;
            return Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0;
        }
    };

    private final String displayName;

    Easing(String displayName) {
        this.displayName = displayName;
    }

    /** Applies the curve to a progress value, which is clamped into {@code [0, 1]} first. */
    public abstract double apply(double t);

    public double applyClamped(double t) {
        return apply(MathUtil.clamp(t, 0.0, 1.0));
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
