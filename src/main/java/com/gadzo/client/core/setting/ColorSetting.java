package com.gadzo.client.core.setting;

import com.gadzo.client.util.ColorUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A packed ARGB colour, rendered as a swatch that opens a hue/saturation picker.
 *
 * <p>Stored as a {@code #AARRGGBB} string so a config file stays hand-editable.
 */
public class ColorSetting extends Setting<Integer> {

    private final boolean allowAlpha;

    public ColorSetting(String name, int defaultValue, boolean allowAlpha) {
        super(name, defaultValue);
        this.allowAlpha = allowAlpha;
    }

    public ColorSetting(String name, int defaultValue) {
        this(name, defaultValue, true);
    }

    public int get() {
        return getValue();
    }

    /** The colour with its alpha multiplied by {@code factor}, for fading panels. */
    public int faded(double factor) {
        return ColorUtil.fade(getValue(), factor);
    }

    public boolean allowsAlpha() {
        return allowAlpha;
    }

    @Override
    protected Integer sanitise(Integer incoming) {
        int value = incoming == null ? getDefaultValue() : incoming;
        return allowAlpha ? value : (value | 0xFF000000);
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(ColorUtil.toHex(getValue()));
    }

    @Override
    public void read(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            setValue(ColorUtil.parseHex(element.getAsString(), getDefaultValue()));
        }
    }
}
