package com.gadzo.client.core.module;

/** Top-level grouping shown as the sidebar of the mods menu. */
public enum ModuleCategory {
    PERFORMANCE("Performance", "Frame time, culling and render budget"),
    HUD("HUD", "On-screen readouts and overlays"),
    VISUAL("Visual", "Look and feel of the world and interface"),
    COMBAT("Combat", "Client-side combat readouts"),
    MOVEMENT("Movement", "Sprint, sneak and camera helpers"),
    SURVIVAL("Survival", "Waypoints, light, food and gear warnings"),
    CREATE("Create", "Live readouts for Create kinetic networks"),
    CLIENT("Client", "GADZO itself — theme, menus and config");

    private final String displayName;
    private final String description;

    ModuleCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
