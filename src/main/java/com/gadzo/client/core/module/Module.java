package com.gadzo.client.core.module;

import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.ColorSetting;
import com.gadzo.client.core.setting.EnumSetting;
import com.gadzo.client.core.setting.KeybindSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.core.setting.Setting;
import com.gadzo.client.util.Animation;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * A single toggleable feature.
 *
 * <p>Subclasses declare their options via the {@code add*} helpers in their constructor and
 * override {@link #onEnable()}/{@link #onDisable()}/{@link #onTick()} as needed. The manager
 * only calls {@link #onTick()} for enabled modules, so implementations never need to re-check
 * their own state.
 */
public abstract class Module {

    /**
     * The game instance.
     *
     * <p>Resolved per call rather than cached in a static field: Fabric runs client
     * entrypoints from inside the {@code Minecraft} constructor, so a field initialised at
     * class-load time can capture {@code null}.
     */
    protected static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private final String name;
    private final String id;
    private final String description;
    private final ModuleCategory category;

    private final List<Setting<?>> settings = new ArrayList<>();
    private final KeybindSetting keybind = new KeybindSetting("Keybind");

    /** Drives the toggle knob and the row highlight in the mods menu. */
    private final Animation toggleAnimation = new Animation(0.0, 220L);

    private boolean enabled;

    /** Modules that are part of the client's own plumbing and cannot be switched off. */
    private boolean permanent;

    /** Hidden from the mods menu; used for internal helpers that still want the lifecycle. */
    private boolean hidden;

    protected Module(String name, String description, ModuleCategory category) {
        this.name = name;
        this.id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        this.description = description;
        this.category = category;
        this.settings.add(keybind);
    }

    // -- identity -------------------------------------------------------------------

    public String getName() {
        return name;
    }

    /** Stable key used by the config file and by {@code ModuleManager} lookups. */
    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public KeybindSetting getKeybind() {
        return keybind;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    /** Settings minus the keybind, which the UI renders in its own dedicated row. */
    public List<Setting<?>> getVisibleSettings() {
        List<Setting<?>> visible = new ArrayList<>(settings.size());
        for (Setting<?> setting : settings) {
            if (setting != keybind && setting.isVisible()) {
                visible.add(setting);
            }
        }
        return visible;
    }

    public Animation getToggleAnimation() {
        return toggleAnimation;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public boolean isHidden() {
        return hidden;
    }

    /**
     * Marks this module as always-on.
     *
     * <p>Permanent modules are enabled immediately: they are client plumbing (the menu
     * launcher, the render-option bridge) rather than optional features, and leaving one
     * switched off would strand the player — the mods menu cannot be reopened if the module
     * that opens it is off.
     */
    protected void markPermanent() {
        this.permanent = true;
        if (!enabled) {
            enabled = true;
            toggleAnimation.snapTo(1.0);
            onEnable();
        }
    }

    protected void markHidden() {
        this.hidden = true;
    }

    // -- state ----------------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (this.enabled == value) {
            return;
        }
        if (!value && permanent) {
            return;
        }
        this.enabled = value;
        toggleAnimation.toBoolean(value);
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Applies a stored enabled-state during config load.
     *
     * <p>Distinct from {@link #setEnabled(boolean)} in that the toggle animation is snapped
     * rather than played, so the menu does not animate every module on startup.
     */
    public void restoreEnabled(boolean value) {
        if (permanent) {
            value = true;
        }
        if (this.enabled != value) {
            this.enabled = value;
            if (value) {
                onEnable();
            } else {
                onDisable();
            }
        }
        toggleAnimation.snapTo(value ? 1.0 : 0.0);
    }

    // -- lifecycle ------------------------------------------------------------------

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Called once per client tick while enabled. */
    public void onTick() {
    }

    /**
     * Called when this module's keybind is pressed.
     *
     * <p>Defaults to toggling. Modules that open a screen or act while held — the menu
     * launcher, zoom — override this instead, which is why the key router does not simply
     * call {@link #toggle()} itself.
     */
    public void onKeybindPressed() {
        toggle();
    }

    /** Called when this module's keybind is released; only hold-style modules need it. */
    public void onKeybindReleased() {
    }

    /** True when the player is in a world and able to act. */
    protected boolean inGame() {
        Minecraft client = mc();
        return client != null && client.player != null && client.level != null;
    }

    // -- setting registration -------------------------------------------------------

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    protected BooleanSetting addBool(String name, boolean defaultValue, String description) {
        return add(new BooleanSetting(name, defaultValue).describe(description));
    }

    protected NumberSetting addNumber(String name, double def, double min, double max, double step, String description) {
        return add(new NumberSetting(name, def, min, max, step).<NumberSetting>describe(description));
    }

    protected <E extends Enum<E>> EnumSetting<E> addEnum(String name, E def, String description) {
        return add(new EnumSetting<>(name, def).describe(description));
    }

    protected <E extends Enum<E>> EnumSetting<E> addEnum(
            String name, E def, Function<E, String> labeller, String description) {
        return add(new EnumSetting<>(name, def, labeller).describe(description));
    }

    protected ColorSetting addColor(String name, int def, String description) {
        return add(new ColorSetting(name, def).describe(description));
    }
}
