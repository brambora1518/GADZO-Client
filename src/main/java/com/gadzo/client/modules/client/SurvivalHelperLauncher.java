package com.gadzo.client.modules.client;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.ui.screen.SurvivalHelperScreen;
import com.gadzo.client.util.Mc;

import org.lwjgl.glfw.GLFW;

/**
 * Opens the survival reference, waypoint list, Nether calculator and food table.
 *
 * <p>Bound to H by default — unused in vanilla, and next to the movement keys so it can be
 * reached without leaving the keyboard.
 */
public class SurvivalHelperLauncher extends Module {

    public SurvivalHelperLauncher() {
        super("Survival helper", "Waypoints, Nether maths, food ranking and reference",
                ModuleCategory.SURVIVAL);
        markPermanent();
        getKeybind().setValue(GLFW.GLFW_KEY_H);
    }

    @Override
    public void onKeybindPressed() {
        open();
    }

    public void open() {
        Mc.openScreen(new SurvivalHelperScreen());
    }
}
