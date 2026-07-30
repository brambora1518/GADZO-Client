package com.gadzo.client.modules.performance;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.util.Mc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Throttles the frame rate when the client does not need a fast one.
 *
 * <p>Alt-tabbing to a browser while a world stays loaded normally keeps the GPU rendering at
 * full speed for nothing. This drops the cap while the window is unfocused, and optionally
 * while a menu is open, then restores it the moment focus returns — worth a lot on laptops
 * and shared machines.
 *
 * <p>The player's own limit is captured on enable and restored on disable, so toggling the
 * module never leaves the game stuck at a reduced cap.
 */
public class DynamicFps extends Module {

    /** Sentinel meaning "no limit captured yet". */
    private static final int NOT_CAPTURED = -1;

    private final NumberSetting unfocusedLimit;
    private final BooleanSetting throttleInMenus;
    private final NumberSetting menuLimit;

    private int savedLimit = NOT_CAPTURED;

    /** The cap we most recently wrote, so we only write on an actual change. */
    private int appliedLimit = NOT_CAPTURED;

    public DynamicFps() {
        super("Dynamic FPS", "Lower the frame cap when the window is not in focus",
                ModuleCategory.PERFORMANCE);

        this.unfocusedLimit = addNumber("Unfocused limit", 10, 1, 60, 1,
                "Frame cap while the game window is in the background")
                .suffix(" fps");
        this.throttleInMenus = addBool("Throttle in menus", false,
                "Also reduce the cap while a menu or inventory is open");
        this.menuLimit = add(new NumberSetting("Menu limit", 60, 10, 240, 5)
                .suffix(" fps")
                .<NumberSetting>describe("Frame cap while a screen is open")
                .visibleWhen(() -> this.throttleInMenus.get()));
    }

    @Override
    protected void onEnable() {
        GameOptions options = options();
        if (options != null) {
            savedLimit = options.getMaxFps().getValue();
        }
    }

    @Override
    protected void onDisable() {
        restore();
    }

    private static GameOptions options() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.options;
    }

    private void restore() {
        GameOptions options = options();
        if (options != null && savedLimit != NOT_CAPTURED) {
            options.getMaxFps().setValue(savedLimit);
        }
        appliedLimit = NOT_CAPTURED;
    }

    @Override
    public void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        GameOptions options = options();
        if (client == null || options == null) {
            return;
        }

        // Capture lazily in case the module was enabled before options were ready.
        if (savedLimit == NOT_CAPTURED) {
            savedLimit = options.getMaxFps().getValue();
        }

        int desired;
        if (!client.isWindowFocused()) {
            desired = unfocusedLimit.getInt();
        } else if (throttleInMenus.get() && Mc.screen() != null) {
            desired = menuLimit.getInt();
        } else {
            desired = savedLimit;
        }

        if (desired != appliedLimit) {
            options.getMaxFps().setValue(desired);
            appliedLimit = desired;
        }
    }
}
