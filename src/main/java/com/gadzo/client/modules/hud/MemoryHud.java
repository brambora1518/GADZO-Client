package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Heap usage, with an optional bar — the quickest way to spot a memory leak mid-session. */
public class MemoryHud extends HudModule {

    private static final double BAR_HEIGHT = 3.0;

    private final BooleanSetting showBar;

    public MemoryHud() {
        super("Memory", "Heap usage", HudAnchor.TOP_RIGHT, -4, 4);
        this.showBar = addBool("Usage bar", true, "Draw a fill bar under the text");
    }

    private String text() {
        return Mc.usedMemoryMb() + " / " + Mc.maxMemoryMb() + " MB";
    }

    private int color() {
        double fraction = Mc.memoryFraction();
        if (fraction > 0.9) return Theme.danger();
        if (fraction > 0.75) return Theme.warning();
        return Theme.textPrimary();
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return font.getWidth(text());
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return font.fontHeight + (showBar.get() ? BAR_HEIGHT + 2 : 0);
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        String value = text();
        line(gfx, font, value, 0, 0, color());

        if (showBar.get()) {
            double width = font.getWidth(value);
            double y = font.fontHeight + 2;
            Render2D.roundedRect(gfx, 0, y, width, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                    ColorUtil.withAlpha(Theme.trackOff(), 180));
            double filled = width * Mc.memoryFraction();
            if (filled > 0.5) {
                Render2D.roundedRect(gfx, 0, y, filled, BAR_HEIGHT, BAR_HEIGHT / 2.0, color());
            }
        }
    }
}
