package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Numeric player stats that vanilla only shows as icons.
 *
 * <p>Saturation in particular has no vanilla display at all despite governing how quickly
 * hunger drains, and armour toughness is invisible without opening the inventory.
 */
public class PlayerStatsHud extends HudModule {

    private final BooleanSetting showHealth;
    private final BooleanSetting showHunger;
    private final BooleanSetting showSaturation;
    private final BooleanSetting showArmour;
    private final BooleanSetting showExperience;

    public PlayerStatsHud() {
        super("PlayerEntity stats", "Health, hunger, saturation, armour and XP as numbers",
                HudAnchor.BOTTOM_RIGHT, -4, -40);
        this.showHealth = addBool("Health", true, "Current and maximum health");
        this.showHunger = addBool("Hunger", true, "Food level out of 20");
        this.showSaturation = addBool("Saturation", true,
                "Hidden in vanilla, but it decides how fast hunger drops");
        this.showArmour = addBool("Armour", true, "Total armour points");
        this.showExperience = addBool("Experience", false, "Level and progress to the next");
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(5);
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return lines;
        }

        if (showHealth.get()) {
            float absorption = player.getAbsorptionAmount();
            String health = String.format("HP %.1f / %.1f", player.getHealth(), player.getMaxHealth());
            if (absorption > 0) {
                health += String.format(" (+%.1f)", absorption);
            }
            lines.add(health);
        }
        if (showHunger.get()) {
            lines.add("Food " + player.getHungerManager().getFoodLevel() + " / 20");
        }
        if (showSaturation.get()) {
            lines.add(String.format("Sat %.1f", player.getHungerManager().getSaturationLevel()));
        }
        if (showArmour.get()) {
            lines.add("Armour " + player.getArmor());
        }
        if (showExperience.get()) {
            lines.add("XP " + player.experienceLevel
                    + " (" + Math.round(player.experienceProgress * 100) + "%)");
        }
        return lines;
    }

    private int colorFor(String line) {
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return Theme.textPrimary();
        }
        if (line.startsWith("HP")) {
            double fraction = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
            if (fraction <= 0.25) return Theme.danger();
            if (fraction <= 0.5) return Theme.warning();
        }
        if (line.startsWith("Food") && player.getHungerManager().getFoodLevel() <= 6) {
            return Theme.warning();
        }
        // Zero saturation is the point at which hunger actually starts draining.
        if (line.startsWith("Sat") && player.getHungerManager().getSaturationLevel() <= 0.0f) {
            return Theme.textMuted();
        }
        return Theme.textPrimary();
    }

    @Override
    public double contentWidth(TextRenderer font) {
        double widest = 0;
        for (String line : lines()) {
            widest = Math.max(widest, font.getWidth(line));
        }
        return widest;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return Math.max(0, lines().size()) * font.fontHeight;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        double y = 0;
        for (String text : lines()) {
            line(gfx, font, text, 0, y, colorFor(text));
            y += font.fontHeight;
        }
    }
}
