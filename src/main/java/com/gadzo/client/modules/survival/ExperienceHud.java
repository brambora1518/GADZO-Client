package com.gadzo.client.modules.survival;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Experience level, and how far off the levels that matter.
 *
 * <p>The vanilla bar shows progress through the current level and nothing else, which is the
 * least useful framing of the number. What a player is usually asking is "how much more mining
 * until I can enchant", and that needs the points remaining, not a fraction of a bar.
 *
 * <p>Level 30 gets its own line because it is the only threshold in the game with a mechanical
 * meaning — it is the cheapest level at which an enchanting table offers its best tier.
 */
public class ExperienceHud extends HudModule {

    /** The level an enchanting table needs for its top-tier offers. */
    private static final int ENCHANT_LEVEL = 30;

    private final BooleanSetting showToNext;
    private final BooleanSetting showToEnchant;
    private final BooleanSetting showTotal;

    public ExperienceHud() {
        super("Experience", "Level, points to the next level and to level 30",
                ModuleCategory.SURVIVAL, HudAnchor.BOTTOM_RIGHT, -6, -108);
        this.showToNext = addBool("To next level", true, "Points still needed for the next level");
        this.showToEnchant = addBool("To level 30", true,
                "Levels remaining before a table offers its best enchantments");
        this.showTotal = addBool("Total", false, "Lifetime experience points collected");
    }

    private int rows() {
        int rows = 1;
        if (showToNext.get()) rows++;
        if (showToEnchant.get()) rows++;
        if (showTotal.get()) rows++;
        return rows;
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return Math.max(96, font.getWidth("Levels to enchant  30"));
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return rows() * (font.fontHeight + 2) - 2;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return;
        }
        double width = contentWidth(font);
        double rowHeight = font.fontHeight + 2;
        double y = 0;

        line(gfx, font, "Level", 0, y, Theme.textSecondary());
        Render2D.textRight(gfx, font, Integer.toString(player.experienceLevel), width, y,
                player.experienceLevel >= ENCHANT_LEVEL ? Theme.accent() : Theme.textPrimary(),
                hasTextShadow());
        y += rowHeight;

        if (showToNext.get()) {
            // getNextLevelExperience() is the size of the current level in points, and
            // experienceProgress is how far through it the player is.
            int remaining = Math.max(0,
                    Math.round((1 - player.experienceProgress) * player.getNextLevelExperience()));
            line(gfx, font, "To next", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font, remaining + " xp", width, y, Theme.textPrimary(),
                    hasTextShadow());
            y += rowHeight;
        }

        if (showToEnchant.get()) {
            int levelsAway = ENCHANT_LEVEL - player.experienceLevel;
            line(gfx, font, "To level 30", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font,
                    levelsAway <= 0 ? "ready" : Integer.toString(levelsAway), width, y,
                    levelsAway <= 0 ? Theme.success() : Theme.warning(), hasTextShadow());
            y += rowHeight;
        }

        if (showTotal.get()) {
            line(gfx, font, "Total", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font, Integer.toString(player.totalExperience), width, y,
                    Theme.textMuted(), hasTextShadow());
        }
    }
}
