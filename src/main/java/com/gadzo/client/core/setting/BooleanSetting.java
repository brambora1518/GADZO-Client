package com.gadzo.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An on/off switch, rendered as an animated toggle in the mods menu. */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public boolean get() {
        return getValue();
    }

    public void toggle() {
        setValue(!getValue());
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void read(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            setValue(element.getAsBoolean());
        }
    }
}
