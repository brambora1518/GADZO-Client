package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Latency to the current server, colour-coded by quality. */
public class PingHud extends HudModule {

    private final BooleanSetting colorCode;

    public PingHud() {
        super("Ping", "Latency to the server", HudAnchor.TOP_LEFT, 4, 46);
        this.colorCode = addBool("Colour code", true, "Green when low, red when high");
    }

    private String text() {
        if (Mc.client() != null && Mc.client().getConnection() == null) {
            return "-- ms";
        }
        return Mc.ping() + " ms";
    }

    private int color() {
        if (!colorCode.get()) {
            return Theme.textPrimary();
        }
        int ping = Mc.ping();
        if (ping <= 0) return Theme.textMuted();
        if (ping < 60) return Theme.success();
        if (ping < 150) return Theme.warning();
        return Theme.danger();
    }

    @Override
    public double contentWidth(Font font) {
        return font.width(text());
    }

    @Override
    public double contentHeight(Font font) {
        return font.lineHeight;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor gfx, Font font) {
        line(gfx, font, text(), 0, 0, color());
    }
}
