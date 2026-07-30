package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/** Shows the current movement state — sprinting, sneaking or flying. */
public class ToggleSprintHud extends HudModule {

    private final BooleanSetting showSneak;
    private final BooleanSetting showFly;

    public ToggleSprintHud() {
        super("Movement state", "Sprint, sneak and fly indicator", HudAnchor.BOTTOM_LEFT, 4, -34);
        this.showSneak = addBool("Sneak", true, "Show when sneaking");
        this.showFly = addBool("Fly", true, "Show when flying");
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(3);
        LocalPlayer player = Mc.player();
        if (player == null) {
            return lines;
        }
        if (player.isSprinting()) {
            lines.add("Sprinting");
        }
        if (showSneak.get() && player.isCrouching()) {
            lines.add("Sneaking");
        }
        if (showFly.get() && player.getAbilities().flying) {
            lines.add("Flying");
        }
        return lines;
    }

    @Override
    public double contentWidth(Font font) {
        double widest = 0;
        for (String line : lines()) {
            widest = Math.max(widest, font.width(line));
        }
        // Reserve the widest possible label so the plate does not resize as state changes.
        return Math.max(widest, font.width("Sprinting"));
    }

    @Override
    public double contentHeight(Font font) {
        return Math.max(1, lines().size()) * font.lineHeight;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor gfx, Font font) {
        double y = 0;
        for (String text : lines()) {
            line(gfx, font, text, 0, y, Theme.accent());
            y += font.lineHeight;
        }
    }
}
