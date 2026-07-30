package com.gadzo.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.lwjgl.glfw.GLFW;

/**
 * A single GLFW key code used to toggle a module.
 *
 * <p>{@link #UNBOUND} models "no key" explicitly rather than using a sentinel the UI would
 * have to special-case. Keys are persisted by their human-readable name so a config survives
 * a GLFW constant change.
 */
public class KeybindSetting extends Setting<Integer> {

    public static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;

    /** Set while the UI is waiting for the user to press the next key. */
    private boolean listening;

    public KeybindSetting(String name, int defaultKey) {
        super(name, defaultKey);
    }

    public KeybindSetting(String name) {
        this(name, UNBOUND);
    }

    public int getKey() {
        return getValue();
    }

    public boolean isBound() {
        return getValue() != UNBOUND;
    }

    public boolean matches(int key) {
        return isBound() && getValue() == key;
    }

    public boolean isListening() {
        return listening;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    /**
     * Accepts a key press while listening.
     *
     * <p>Escape clears the binding instead of binding escape itself, matching the behaviour
     * players expect from every other client.
     *
     * @return whether the press was consumed
     */
    public boolean acceptPress(int key) {
        if (!listening) {
            return false;
        }
        setValue(key == GLFW.GLFW_KEY_ESCAPE ? UNBOUND : key);
        listening = false;
        return true;
    }

    /** Label for the keybind button; shows the pending state while listening. */
    public String display() {
        if (listening) {
            return "...";
        }
        return isBound() ? keyName(getValue()) : "NONE";
    }

    public static String keyName(int key) {
        if (key == UNBOUND) {
            return "NONE";
        }
        // GLFW only names printable keys; fall back to a readable constant for the rest.
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isBlank()) {
            return name.toUpperCase();
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_PAGE_UP -> "PAGE UP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE DOWN";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            default -> {
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                }
                yield "KEY " + key;
            }
        };
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void read(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            try {
                setValue(element.getAsInt());
            } catch (NumberFormatException ignored) {
                // Corrupt entry: keep the default binding.
            }
        }
    }
}
