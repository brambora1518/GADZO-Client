package com.gadzo.client.core.hud;

import com.gadzo.client.GadzoClient;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws every enabled HUD element each frame.
 *
 * <p>Registered as a single {@link HudRenderCallback} rather than one per module, so the
 * ordering between GADZO readouts is ours to control.
 *
 * <p>Unlike the 26.2 branch — where Fabric's HUD element registry sits inside vanilla's own
 * gated HUD pass — this callback fires regardless of whether the GUI is hidden, so the F1
 * check has to be made here.
 */
public final class HudManager {

    /** Set by the HUD editor so live elements are suppressed while the editor is open. */
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
        HudRenderCallback.EVENT.register(HudManager::renderAll);
    }

    private static void renderAll(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options.hudHidden) {
            return;
        }
        // The editor draws its own copy of every element over a dimmed backdrop.
        if (editorOpen) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        for (HudModule module : GadzoClient.modules().hudModules()) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.render(context, client.textRenderer, width, height, 1.0);
            } catch (Exception e) {
                GadzoClient.LOGGER.error("HUD element '{}' failed to render and was disabled",
                        module.getId(), e);
                module.setEnabled(false);
            }
        }
    }
}
