package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.input.InputTracker;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Clicks per second for the left and (optionally) right mouse buttons. */
public class CpsHud extends HudModule {

    private final BooleanSetting showRight;
    private final BooleanSetting compact;

    public CpsHud() {
        super("CPS", "Clicks per second", HudAnchor.TOP_LEFT, 4, 18);
        this.showRight = addBool("Right button", true, "Also show right-click CPS");
        this.compact = addBool("Compact", false, "Show as \"6 | 2\" instead of labelled lines");
    }

    private String text() {
        int left = InputTracker.leftCps();
        if (!showRight.get()) {
            return compact.get() ? Integer.toString(left) : left + " CPS";
        }
        int right = InputTracker.rightCps();
        return compact.get() ? left + " | " + right : left + " CPS  " + right + " RCPS";
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return font.getWidth(text());
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return font.fontHeight;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        line(gfx, font, text(), 0, 0, Theme.textPrimary());
    }
}
