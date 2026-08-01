package com.gadzo.client.modules.client;

import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.ColorSetting;
import com.gadzo.client.core.setting.EnumSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.ui.Theme;

/**
 * The client's own appearance and behaviour.
 *
 * <p>Every option here writes straight through to {@link Theme}, so changes are visible in
 * the menu that is being used to make them.
 */
public class ClientSettings extends Module {

    private final EnumSetting<Theme.Appearance> appearance;
    private final EnumSetting<Theme.AccentMode> accentMode;
    private final ColorSetting accentPrimary;
    private final ColorSetting accentSecondary;
    private final NumberSetting accentSpeed;
    private final NumberSetting cornerRadius;
    private final BooleanSetting blur;
    private final BooleanSetting autoSave;

    public ClientSettings() {
        super("Appearance", "Theme, accent colour and menu styling", ModuleCategory.CLIENT);
        markPermanent();

        this.appearance = addEnum("Theme", Theme.Appearance.DARK, "Base palette");
        this.appearance.onChange(Theme::setAppearance);

        this.accentMode = addEnum("Accent mode", Theme.AccentMode.GRADIENT,
                "Static colour, animated gradient, or full rainbow");
        this.accentMode.onChange(Theme::setAccentMode);

        this.accentPrimary = addColor("Accent", 0xFF5B8CFF, "Primary accent colour");
        this.accentPrimary.onChange(Theme::setAccentPrimary);

        this.accentSecondary = add(new ColorSetting("Accent 2", 0xFF9B5BFF)
                .<ColorSetting>describe("Second colour of the gradient")
                .visibleWhen(() -> this.accentMode.is(Theme.AccentMode.GRADIENT)));
        this.accentSecondary.onChange(Theme::setAccentSecondary);

        this.accentSpeed = add(new NumberSetting("Accent speed", 6000, 1000, 20000, 500)
                .suffix(" ms")
                .<NumberSetting>describe("Time for one full colour cycle")
                .visibleWhen(() -> !this.accentMode.is(Theme.AccentMode.STATIC)));
        this.accentSpeed.onChange(value -> Theme.setRainbowPeriod((int) Math.round(value)));

        // Default read from Theme, never written out again here: this setting is applied to
        // the theme at startup, so a second copy of the number would silently win.
        this.cornerRadius = addNumber("Corner radius", Theme.DEFAULT_RADIUS, 0, 20, 1,
                "How rounded every panel and control is");
        this.cornerRadius.styleOwned();
        this.cornerRadius.onChange(Theme::setCornerRadius);

        this.blur = addBool("Background blur", true,
                "Frost the world behind the menus; costs a little GPU time");
        this.blur.onChange(Theme::setBlurEnabled);

        this.autoSave = addBool("Auto-save config", true,
                "Write the profile whenever a menu closes");
    }

    public boolean shouldAutoSave() {
        return autoSave.get();
    }

    /** Pushes every value into the theme; used after loading a profile. */
    public void applyAll() {
        Theme.setAppearance(appearance.get());
        Theme.setAccentMode(accentMode.get());
        Theme.setAccentPrimary(accentPrimary.get());
        Theme.setAccentSecondary(accentSecondary.get());
        Theme.setRainbowPeriod(accentSpeed.getInt());
        Theme.setCornerRadius(cornerRadius.get());
        Theme.setBlurEnabled(blur.get());
    }

    @Override
    protected void onEnable() {
        applyAll();
    }

    /** Writes the active profile to disk. */
    public void save() {
        ConfigManager.save();
    }
}
