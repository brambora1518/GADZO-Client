package com.gadzo.client.modules.client;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.ui.screen.CreateHelperScreen;
import com.gadzo.client.util.Mc;

import org.lwjgl.glfw.GLFW;

/**
 * Opens the Create reference and stress planner.
 *
 * <p>Create has no build for MinecraftClient 26.2 — Forge/NeoForge stops at 1.21.1 and the Fabric
 * port at 1.20.1 — so this is a planning tool rather than a live overlay. It is still worth
 * having in the client: the stress calculator answers "how many water wheels does this need"
 * without a spreadsheet, and the reference covers the parts of Create that trip people up
 * regardless of version.
 */
public class CreateHelperLauncher extends Module {

    public CreateHelperLauncher() {
        super("Create helper", "Reference and stress planner for the Create mod",
                ModuleCategory.CLIENT);
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
