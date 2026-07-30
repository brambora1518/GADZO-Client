package com.gadzo.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Function;

/**
 * A choice between the constants of an enum, rendered as a dropdown.
 *
 * <p>Values are persisted by {@link Enum#name()} so reordering or inserting constants later
 * does not silently reinterpret an existing config.
 *
 * @param <E> the enum type being selected
 */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {

    private final E[] constants;
    private final Function<E, String> labeller;

    public EnumSetting(String name, E defaultValue, Function<E, String> labeller) {
        super(name, defaultValue);
        this.constants = defaultValue.getDeclaringClass().getEnumConstants();
        this.labeller = labeller;
    }

    public EnumSetting(String name, E defaultValue) {
        this(name, defaultValue, Object::toString);
    }

    public E get() {
        return getValue();
    }

    public boolean is(E other) {
        return getValue() == other;
    }

    public E[] constants() {
        return constants.clone();
    }

    public String label(E constant) {
        return labeller.apply(constant);
    }

    public String currentLabel() {
        return labeller.apply(getValue());
    }

    /** Advances to the next constant, wrapping at the end. */
    public void cycle() {
        setValue(constants[(getValue().ordinal() + 1) % constants.length]);
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(getValue().name());
    }

    @Override
    public void read(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }
        String name = element.getAsString();
        for (E constant : constants) {
            if (constant.name().equals(name)) {
                setValue(constant);
                return;
            }
        }
        // Unknown constant (config from a newer or older build): leave the default in place.
    }
}
