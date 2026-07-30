package com.gadzo.client.modules.client;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.ui.screen.HudEditorScreen;
import com.gadzo.client.util.Mc;

import org.lwjgl.glfw.GLFW;

/** Opens the drag-and-drop HUD editor. */
public class HudEditorLauncher extends Module {

    public HudEditorLauncher() {
        super("HUD editor", "Rearrange your HUD by dragging", ModuleCategory.CLIENT);
        markPermanent();
        getKeybind().setValue(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @Override
    public void onKeybindPressed() {
        open();
    }

    public void open() {
        Mc.openScreen(new HudEditorScreen());
    }
}
