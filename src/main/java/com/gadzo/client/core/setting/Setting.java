package com.gadzo.client.core.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Base class for a single configurable value belonging to a module.
 *
 * <p>A setting owns its name, its description (shown as a tooltip in the mods menu), an
 * optional visibility predicate for dependent options, and the serialisation hooks used by
 * the config manager. Subclasses supply the value type and the widget the UI builds for it.
 *
 * @param <T> the value type carried by this setting
 */
public abstract class Setting<T> {

    private final String name;
    private final String id;
    private final T defaultValue;

    private String description = "";
    private T value;
    private BooleanSupplier visibility = () -> true;
    private boolean styleOwned;
    private final List<Consumer<T>> listeners = new ArrayList<>(0);

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.id = name.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    /** Stable key used in the config file; derived from the display name. */
    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public T getValue() {
        return value;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public void setValue(T newValue) {
        T sanitised = sanitise(newValue);
        if (this.value != null && this.value.equals(sanitised)) {
            return;
        }
        this.value = sanitised;
        for (Consumer<T> listener : listeners) {
            listener.accept(sanitised);
        }
    }

    public void reset() {
        setValue(defaultValue);
    }

    public boolean isDefault() {
        return value != null && value.equals(defaultValue);
    }

    /**
     * Whether the mods menu should currently draw this setting. Used to hide options that
     * only make sense when a parent toggle is on.
     */
    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    /** Hook for subclasses to coerce an incoming value into a legal one (clamping, etc). */
    protected T sanitise(T incoming) {
        return incoming;
    }

    // -- builder-style configuration ------------------------------------------------

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S describe(String description) {
        this.description = description;
        return (S) this;
    }

    /**
     * Marks this setting as one whose default the client owns rather than the player.
     *
     * <p>Corner radius is the motivating case, and it is worth spelling out because it cost
     * four rounds of "the UI looks the same". The radius is persisted like any other setting,
     * so the value written on a player's first launch outlived every later change to the
     * default: a restyle could not reach anyone who had already run the client once.
     *
     * <p>A style-owned setting is skipped when loading a profile written under an older
     * {@link com.gadzo.client.ui.Theme#STYLE_VERSION}. The new default lands exactly once,
     * and colours the player picked on purpose are untouched.
     */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S styleOwned() {
        this.styleOwned = true;
        return (S) this;
    }

    public boolean isStyleOwned() {
        return styleOwned;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier predicate) {
        this.visibility = predicate;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Consumer<T> listener) {
        this.listeners.add(listener);
        return (S) this;
    }

    // -- persistence ----------------------------------------------------------------

    public abstract JsonElement write();

    public abstract void read(JsonElement element);
}
