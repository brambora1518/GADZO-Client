package com.gadzo.client.modules.visual;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.NumberSetting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Raises the brightness option beyond the slider's normal ceiling.
 *
 * <p>Implemented by writing {@code gamma} directly rather than by touching the lighting
 * pipeline, so it composes correctly with shaders and resource packs. The player's original
 * value is restored on disable.
 */
public class Fullbright extends Module {

    private static final double NOT_CAPTURED = Double.NaN;

    private final NumberSetting brightness;

    private double savedGamma = NOT_CAPTURED;

    public Fullbright() {
        super("Fullbright", "See in the dark without a night-vision potion", ModuleCategory.VISUAL);
        this.brightness = addNumber("Brightness", 10.0, 1.0, 15.0, 0.5,
                "Gamma multiplier; 1.0 is the vanilla maximum");
        this.brightness.onChange(value -> {
            if (isEnabled()) {
                apply();
            }
        });
    }

    private static GameOptions options() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.options;
    }

    private void apply() {
        GameOptions options = options();
        if (options != null) {
            options.getGamma().setValue(brightness.get());
        }
    }

    @Override
    protected void onEnable() {
        GameOptions options = options();
        if (options != null) {
            savedGamma = options.getGamma().getValue();
            apply();
        }
    }

    @Override
    protected void onDisable() {
        GameOptions options = options();
        if (options != null && !Double.isNaN(savedGamma)) {
            options.getGamma().setValue(savedGamma);
            savedGamma = NOT_CAPTURED;
        }
    }
}
