package com.gadzo.client.core.setting;

import com.gadzo.client.util.MathUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A bounded numeric value rendered as a slider.
 *
 * <p>The setting owns its own bounds and step so the UI never has to guess how to quantise
 * a drag; {@link #setFromFraction(double)} is the entry point the slider widget uses.
 */
public class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;
    private final int decimals;
    private String suffix = "";

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = decimalsFor(step);
        // The super constructor stored the raw default; re-run it through sanitise so an
        // out-of-range default can never leak into the UI.
        setValue(defaultValue);
    }

    private static int decimalsFor(double step) {
        if (step >= 1.0) return 0;
        if (step >= 0.1) return 1;
        if (step >= 0.01) return 2;
        return 3;
    }

    @Override
    protected Double sanitise(Double incoming) {
        double clamped = MathUtil.clamp(incoming == null ? min : incoming, min, max);
        return MathUtil.round(MathUtil.roundToStep(clamped, step), decimals);
    }

    public double get() {
        return getValue();
    }

    public int getInt() {
        return (int) Math.round(getValue());
    }

    public float getFloat() {
        return getValue().floatValue();
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    /** Position of the current value within the range, in {@code [0, 1]}. */
    public double asFraction() {
        if (max - min == 0.0) return 0.0;
        return MathUtil.clamp((getValue() - min) / (max - min), 0.0, 1.0);
    }

    /** Sets the value from a normalised slider position. */
    public void setFromFraction(double fraction) {
        setValue(min + MathUtil.clamp(fraction, 0.0, 1.0) * (max - min));
    }

    public NumberSetting suffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    /** Formatted for display next to the slider, e.g. {@code "120 fps"}. */
    public String display() {
        String number = decimals == 0
                ? Integer.toString(getInt())
                : String.format("%." + decimals + "f", getValue());
        return suffix.isEmpty() ? number : number + suffix;
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void read(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            try {
                setValue(element.getAsDouble());
            } catch (NumberFormatException ignored) {
                // Corrupt entry: keep whatever the default already gave us.
            }
        }
    }
}
