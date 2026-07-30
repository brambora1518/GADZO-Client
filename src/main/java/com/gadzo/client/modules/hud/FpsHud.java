package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.EnumSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Frame-rate readout, optionally colour-coded by how healthy the frame rate is. */
public class FpsHud extends HudModule {

    /** How the number is labelled. */
    public enum Style {
        PLAIN("120"),
        SUFFIX("120 FPS"),
        PREFIX("FPS: 120");

        private final String example;

        Style(String example) {
            this.example = example;
        }

        @Override
        public String toString() {
            return example;
        }
    }

    private final EnumSetting<Style> style;
    private final BooleanSetting colorCode;

    public FpsHud() {
        super("FPS", "Frames per second", HudAnchor.TOP_LEFT, 4, 4);
        this.style = addEnum("Style", Style.SUFFIX, "How the value is labelled");
        this.colorCode = addBool("Colour code", true, "Green when smooth, red when struggling");
    }

    private String text() {
        int fps = Mc.client() == null ? 0 : Mc.client().getCurrentFps();
        return switch (style.get()) {
            case PLAIN -> Integer.toString(fps);
            case SUFFIX -> fps + " FPS";
            case PREFIX -> "FPS: " + fps;
        };
    }

    private int color() {
        if (!colorCode.get()) {
            return Theme.textPrimary();
        }
        int fps = Mc.client() == null ? 0 : Mc.client().getCurrentFps();
        if (fps >= 120) return Theme.success();
        if (fps >= 60) return Theme.textPrimary();
        if (fps >= 30) return Theme.warning();
        return Theme.danger();
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
        line(gfx, font, text(), 0, 0, color());
    }
}
