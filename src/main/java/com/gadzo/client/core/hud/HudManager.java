package com.gadzo.client.core.hud;

import com.gadzo.client.GadzoClient;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws every enabled HUD element each frame.
 *
 * <p>Registered as a single Fabric HUD element rather than one per module: the ordering
 * between GADZO readouts is then ours to control, and vanilla only sees one entry to sort.
 */
public final class HudManager {

    /** Set by the HUD editor so live elements dim while the editor overlay is open. */
    private static boolean editorOpen;

    private HudManager() {
    }

    public static void setEditorOpen(boolean open) {
        editorOpen = open;
    }

    public static boolean isEditorOpen() {
        return editorOpen;
    }

    public static void register() {
        HudElementRegistry.addLast(GadzoClient.id("hud"), HudManager::renderAll);
    }

    private static void renderAll(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        // No F1 check needed: this element is registered inside vanilla's HUD pass, which
        // vanilla already skips when the GUI is hidden.
        // The editor draws its own copy of every element on top of a dimmed backdrop, so
        // suppress the live pass to avoid double-drawing.
        if (editorOpen) {
            return;
        }

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        for (HudModule module : GadzoClient.modules().hudModules()) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.render(gfx, client.font, width, height, 1.0);
            } catch (Exception e) {
                GadzoClient.LOGGER.error("HUD element '{}' failed to render and was disabled",
                        module.getId(), e);
                module.setEnabled(false);
            }
        }
    }
}
