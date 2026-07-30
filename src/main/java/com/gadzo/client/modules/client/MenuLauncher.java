package com.gadzo.client.modules.client;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.ui.screen.ClickGuiScreen;
import com.gadzo.client.util.Mc;

import org.lwjgl.glfw.GLFW;

/**
 * Opens the mods menu.
 *
 * <p>Modelled as a module so its keybind is edited in exactly the same place as every other
 * binding. It is permanent — a player who disabled the only way back into the menu would have
 * to edit the config file by hand.
 */
public class MenuLauncher extends Module {

    public MenuLauncher() {
        super("Mods menu", "Open the GADZO mods menu", ModuleCategory.CLIENT);
        markPermanent();
        getKeybind().setValue(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onKeybindPressed() {
        open();
    }

    public void open() {
        Mc.openScreen(new ClickGuiScreen());
    }
}
