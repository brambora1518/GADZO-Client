package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.input.CombatTracker;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Consecutive-hit counter, hidden while no combo is running. */
public class ComboHud extends HudModule {

    private final BooleanSetting hideWhenIdle;

    public ComboHud() {
        super("Combo", "Consecutive hits on one target", ModuleCategory.COMBAT,
                HudAnchor.MIDDLE_CENTER, 0, -40);
        this.hideWhenIdle = addBool("Hide when idle", true, "Only appear once a combo starts");
    }

    private String text() {
        int combo = CombatTracker.combo();
        return combo + (combo == 1 ? " hit" : " hits");
    }

    private boolean visible() {
        return !hideWhenIdle.get() || CombatTracker.combo() > 0;
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return visible() ? font.getWidth(text()) : 0;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return visible() ? font.fontHeight : 0;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        if (!visible()) {
            return;
        }
        line(gfx, font, text(), 0, 0, Theme.accent());
    }
}
