package com.gadzo.client.modules.client;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.ui.screen.CreateHelperScreen;
import com.gadzo.client.util.Mc;

import org.lwjgl.glfw.GLFW;

/**
 * Opens the Create reference, stress planner and gear-ratio solver.
 *
 * <p>Useful with or without Create installed. With it, the stress figures come out of Create's
 * own registry; without it, the planner falls back to built-in defaults so a contraption can be
 * costed before the mod is even in the pack.
 */
public class CreateHelperLauncher extends Module {

    public CreateHelperLauncher() {
        super("Create helper", "Reference, stress planner and gear ratios for Create",
                ModuleCategory.CREATE);
        markPermanent();
        getKeybind().setValue(GLFW.GLFW_KEY_G);
    }

    @Override
    public void onKeybindPressed() {
        open();
    }

    public void open() {
        Mc.openScreen(new CreateHelperScreen());
    }
}
