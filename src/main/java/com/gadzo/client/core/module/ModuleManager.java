package com.gadzo.client.core.module;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.hud.HudModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry and tick driver for every {@link Module} in the client.
 *
 * <p>Registration order is preserved so the mods menu lists modules in the order they were
 * declared rather than in hash order.
 */
public class ModuleManager {

    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final List<Module> ordered = new ArrayList<>();
    private final List<HudModule> hudModules = new ArrayList<>();

    public void register(Module module) {
        if (byId.putIfAbsent(module.getId(), module) != null) {
            throw new IllegalStateException("Duplicate module id: " + module.getId());
        }
        ordered.add(module);
        if (module instanceof HudModule hud) {
            hudModules.add(hud);
        }
    }

    public void registerAll(Module... modules) {
        for (Module module : modules) {
            register(module);
        }
    }

    /**
     * Enables every permanent module.
     *
     * <p>Must run after all modules are constructed. {@code markPermanent()} cannot enable
     * them itself: subclasses call it from their constructor, so {@code onEnable()} would run
     * against fields that have not been assigned yet.
     */
    public void activatePermanentModules() {
        for (Module module : ordered) {
            try {
                module.activate();
            } catch (Exception e) {
                GadzoClient.LOGGER.error("Permanent module '{}' failed to activate",
                        module.getId(), e);
            }
        }
    }

    public List<Module> all() {
        return Collections.unmodifiableList(ordered);
    }

    public List<HudModule> hudModules() {
        return Collections.unmodifiableList(hudModules);
    }

    public Module byId(String id) {
        return byId.get(id);
    }

    /** All non-hidden modules in a category, in registration order. */
    public List<Module> byCategory(ModuleCategory category) {
        List<Module> result = new ArrayList<>();
        for (Module module : ordered) {
            if (module.getCategory() == category && !module.isHidden()) {
                result.add(module);
            }
        }
        return result;
    }

    /** Case-insensitive substring match over name and description, for the menu search box. */
    public List<Module> search(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return all();
        }
        List<Module> result = new ArrayList<>();
        for (Module module : ordered) {
            if (module.isHidden()) {
                continue;
            }
            if (module.getName().toLowerCase(Locale.ROOT).contains(needle)
                    || module.getDescription().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(module);
            }
        }
        return result;
    }

    public long enabledCount() {
        return ordered.stream().filter(Module::isEnabled).count();
    }

    /**
     * Ticks every enabled module.
     *
     * <p>A module that throws is disabled rather than allowed to break the tick loop for
     * everything after it — a broken optional feature should not take the client down.
     */
    public void tick() {
        for (Module module : ordered) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onTick();
            } catch (Exception e) {
                GadzoClient.LOGGER.error("Module '{}' threw during tick and was disabled", module.getId(), e);
                try {
                    module.setEnabled(false);
                } catch (Exception suppressed) {
                    GadzoClient.LOGGER.error("Failed to disable module '{}'", module.getId(), suppressed);
                }
            }
        }
    }

    /**
     * Routes a key press to any module bound to it.
     *
     * <p>Permanent modules are included: their bindings open screens or act while held, so
     * skipping them would make the mods menu unreachable.
     *
     * @return whether at least one module consumed the press
     */
    public boolean handleKeyPress(int key) {
        boolean handled = false;
        for (Module module : ordered) {
            if (!module.getKeybind().matches(key)) {
                continue;
            }
            try {
                module.onKeybindPressed();
            } catch (Exception e) {
                GadzoClient.LOGGER.error("Module '{}' threw handling its keybind", module.getId(), e);
            }
            handled = true;
        }
        return handled;
    }

    /** Routes a key release, for modules that act only while their binding is held. */
    public boolean handleKeyRelease(int key) {
        boolean handled = false;
        for (Module module : ordered) {
            if (!module.getKeybind().matches(key)) {
                continue;
            }
            try {
                module.onKeybindReleased();
            } catch (Exception e) {
                GadzoClient.LOGGER.error("Module '{}' threw releasing its keybind", module.getId(), e);
            }
            handled = true;
        }
        return handled;
    }
}
