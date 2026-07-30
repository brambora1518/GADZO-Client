package com.gadzo.client.modules.performance;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.util.Mc;

import net.minecraft.client.option.GameOptions;

/**
 * Scales down the full-screen distortion effects.
 *
 * <p>Nausea, portal warp and the darkness pulse are all full-screen passes; turning them
 * down costs nothing visually in a competitive context and removes their per-frame work.
 * These are also the effects most likely to cause motion discomfort, so the module doubles
 * as an accessibility control.
 */
public class ScreenEffects extends Module {

    private final NumberSetting distortion;
    private final NumberSetting fovEffect;
    private final NumberSetting darkness;
    private final NumberSetting glintSpeed;

    public ScreenEffects() {
        super("Screen effects", "Reduce nausea, portal and darkness distortion",
                ModuleCategory.PERFORMANCE);

        this.distortion = addNumber("Distortion", 0, 0, 100, 5,
                "Nausea and portal warp intensity")
                .suffix("%");
        this.fovEffect = addNumber("FOV effect", 0, 0, 100, 5,
                "Field-of-view change from speed and sprinting")
                .suffix("%");
        this.darkness = addNumber("Darkness pulse", 0, 0, 100, 5,
                "Intensity of the Warden's darkness effect")
                .suffix("%");
        this.glintSpeed = addNumber("Glint speed", 50, 0, 100, 5,
                "Animation speed of the enchantment shimmer")
                .suffix("%");

        for (var setting : getSettings()) {
            setting.onChange(ignored -> {
                if (isEnabled()) {
                    apply();
                }
            });
        }
    }

    private void apply() {
        GameOptions options = Mc.client() == null ? null : Mc.client().options;
        if (options == null) {
            return;
        }
        options.getDistortionEffectScale().setValue(distortion.get() / 100.0);
        options.getFovEffectScale().setValue(fovEffect.get() / 100.0);
        options.getDarknessEffectScale().setValue(darkness.get() / 100.0);
        options.getGlintSpeed().setValue(glintSpeed.get() / 100.0);
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        // Restore vanilla defaults rather than whatever the player had, since these sliders
        // are almost always left at full and re-reading them adds state for no benefit.
        GameOptions options = Mc.client() == null ? null : Mc.client().options;
        if (options == null) {
            return;
        }
        options.getDistortionEffectScale().setValue(1.0);
        options.getFovEffectScale().setValue(1.0);
        options.getDarknessEffectScale().setValue(1.0);
        options.getGlintSpeed().setValue(0.5);
    }
}
