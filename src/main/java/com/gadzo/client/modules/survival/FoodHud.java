package com.gadzo.client.modules.survival;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.HungerManager;

/**
 * Hunger, saturation and exhaustion, with the part vanilla hides.
 *
 * <p>The hunger bar shows food level and nothing else, but saturation is what actually decides
 * whether you regenerate and how long before the bar starts dropping. A player at 20 food with
 * no saturation and a player at 20 food with 20 saturation look identical on screen and behave
 * completely differently — one is about to start losing hunger, the other has minutes in hand.
 *
 * <p>All three values are already on the client: the server sends food and saturation in the
 * health update packet, and exhaustion is tracked locally. Nothing here is inferred.
 */
public class FoodHud extends HudModule {

    private static final double WIDTH = 104.0;
    private static final double BAR_HEIGHT = 4.0;

    /** Food level at or above which the player regenerates health. */
    private static final int REGEN_THRESHOLD = 18;

    /** Below this, the player takes starvation damage and cannot sprint. */
    private static final int SPRINT_THRESHOLD = 6;

    private final BooleanSetting showSaturation;
    private final BooleanSetting showExhaustion;
    private final BooleanSetting showStatus;

    public FoodHud() {
        super("Food", "Hunger, saturation and whether you are healing",
                ModuleCategory.SURVIVAL, HudAnchor.BOTTOM_RIGHT, -6, -60);
        this.showSaturation = addBool("Saturation", true,
                "Show the hidden saturation value behind the hunger bar");
        this.showExhaustion = addBool("Exhaustion", false,
                "Show the exhaustion counter that drains saturation");
        this.showStatus = addBool("Status", true, "Say whether you are healing or starving");
    }

    private HungerManager hunger() {
        ClientPlayerEntity player = Mc.player();
        return player == null ? null : player.getHungerManager();
    }

    private int rows() {
        int rows = 1;
        if (showSaturation.get()) rows++;
        if (showExhaustion.get()) rows++;
        if (showStatus.get()) rows++;
        return rows;
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return WIDTH;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return rows() * (font.fontHeight + 2) + BAR_HEIGHT + 2;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        HungerManager manager = hunger();
        if (manager == null) {
            return;
        }
        int food = manager.getFoodLevel();
        float saturation = manager.getSaturationLevel();

        double y = 0;
        double rowHeight = font.fontHeight + 2;

        line(gfx, font, "Hunger", 0, y, Theme.textSecondary());
        Render2D.textRight(gfx, font, food + " / 20", WIDTH, y, foodColor(food), hasTextShadow());
        y += rowHeight;

        // One bar, two values: hunger fills it, saturation overlays the part of it that is
        // protected. That is the relationship the numbers alone do not convey.
        Render2D.roundedRect(gfx, 0, y, WIDTH, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                ColorUtil.withAlpha(Theme.trackOff(), 190));
        double foodWidth = WIDTH * MathUtil.clamp(food / 20.0, 0.0, 1.0);
        if (foodWidth > 0.5) {
            Render2D.roundedRect(gfx, 0, y, foodWidth, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                    foodColor(food));
        }
        double saturationWidth = WIDTH * MathUtil.clamp(saturation / 20.0, 0.0, 1.0);
        if (saturationWidth > 0.5) {
            Render2D.roundedRect(gfx, 0, y + 1, saturationWidth, BAR_HEIGHT - 2,
                    (BAR_HEIGHT - 2) / 2.0, ColorUtil.withAlpha(0xFFFFD24D, 220));
        }
        y += BAR_HEIGHT + 2;

        if (showSaturation.get()) {
            line(gfx, font, "Saturation", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font, String.format("%.1f", saturation), WIDTH, y,
                    saturation > 0 ? Theme.textPrimary() : Theme.warning(), hasTextShadow());
            y += rowHeight;
        }

        if (showExhaustion.get()) {
            line(gfx, font, "Exhaustion", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font, String.format("%.1f / 4", manager.getExhaustion()),
                    WIDTH, y, Theme.textMuted(), hasTextShadow());
            y += rowHeight;
        }

        if (showStatus.get()) {
            line(gfx, font, status(food, saturation), 0, y, statusColor(food));
        }
    }

    /**
     * The one sentence that matters about the current state.
     *
     * <p>Ordered by urgency rather than by value: starving outranks "not healing", and a player
     * who cannot sprint wants to know that before they want to know their saturation is empty.
     */
    private String status(int food, float saturation) {
        if (food <= 0) {
            return "Starving — taking damage";
        }
        if (food < SPRINT_THRESHOLD) {
            return "Too hungry to sprint";
        }
        if (food >= REGEN_THRESHOLD) {
            return saturation > 0 ? "Healing quickly" : "Healing";
        }
        return "Not healing — eat to reach 18";
    }

    private int statusColor(int food) {
        if (food <= 0 || food < SPRINT_THRESHOLD) {
            return Theme.danger();
        }
        return food >= REGEN_THRESHOLD ? Theme.success() : Theme.warning();
    }

    private int foodColor(int food) {
        if (food < SPRINT_THRESHOLD) return Theme.danger();
        if (food < REGEN_THRESHOLD) return Theme.warning();
        return Theme.success();
    }
}
