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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/**
 * Light level where you stand, and whether hostile mobs can spawn there.
 *
 * <p>The same figures F3 shows, laid out for the question people actually have while lighting a
 * base up. The verdict is the useful part: since 1.18 hostile mobs need a block light level of
 * exactly zero, so a single torch's worth of light anywhere in a room makes that spot safe, and
 * "light level 7 or below is dangerous" is advice from an older version of the game.
 *
 * <p>Sky light is shown alongside because it is what decides whether an outdoor spot is safe by
 * day and dangerous by night — block light alone would call an open field safe at noon and give
 * the same answer at midnight.
 */
public class LightLevelHud extends HudModule {

    private final BooleanSetting showSky;
    private final BooleanSetting showVerdict;
    private final BooleanSetting onlyWhenSpawnable;

    public LightLevelHud() {
        super("Light level", "Block light, sky light and whether mobs can spawn here",
                ModuleCategory.SURVIVAL, HudAnchor.BOTTOM_LEFT, 6, -60);
        this.showSky = addBool("Sky light", true, "Include the sky light level");
        this.showVerdict = addBool("Spawn verdict", true,
                "Say whether hostile mobs can spawn on this block");
        this.onlyWhenSpawnable = addBool("Only when unsafe", false,
                "Hide the element unless mobs could spawn where you are standing");
    }

    private BlockPos feet() {
        ClientPlayerEntity player = Mc.player();
        return player == null ? null : player.getBlockPos();
    }

    private int blockLight() {
        ClientWorld world = Mc.client() == null ? null : Mc.client().world;
        BlockPos pos = feet();
        return world == null || pos == null ? 0 : world.getLightLevel(LightType.BLOCK, pos);
    }

    private int skyLight() {
        ClientWorld world = Mc.client() == null ? null : Mc.client().world;
        BlockPos pos = feet();
        return world == null || pos == null ? 0 : world.getLightLevel(LightType.SKY, pos);
    }

    private boolean spawnable() {
        return blockLight() == 0;
    }

    private boolean visible() {
        return !onlyWhenSpawnable.get() || spawnable();
    }

    private int rows() {
        int rows = 1;
        if (showSky.get()) rows++;
        if (showVerdict.get()) rows++;
        return rows;
    }

    @Override
    public double contentWidth(TextRenderer font) {
        if (!visible()) {
            return 0;
        }
        return Math.max(font.getWidth("Block light  15"),
                showVerdict.get() ? font.getWidth("Mobs can spawn here") : 0);
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return visible() ? rows() * (font.fontHeight + 2) - 2 : 0;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        if (!visible() || Mc.player() == null) {
            return;
        }
        double y = 0;
        double rowHeight = font.fontHeight + 2;
        double contentWidth = contentWidth(font);

        int block = blockLight();
        line(gfx, font, "Block light", 0, y, Theme.textSecondary());
        Render2D.textRight(gfx, font, Integer.toString(block), contentWidth, y,
                lightColor(block), hasTextShadow());
        y += rowHeight;

        if (showSky.get()) {
            int sky = skyLight();
            line(gfx, font, "Sky light", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font, Integer.toString(sky), contentWidth, y,
                    Theme.textMuted(), hasTextShadow());
            y += rowHeight;
        }

        if (showVerdict.get()) {
            boolean unsafe = spawnable();
            line(gfx, font, unsafe ? "Mobs can spawn here" : "No spawns here", 0, y,
                    unsafe ? Theme.danger() : Theme.success());
        }
    }

    /**
     * Colour for a block light value.
     *
     * <p>Only zero is red, because only zero permits a spawn. Grading it like a gradient would
     * imply that 3 is more dangerous than 8, which has not been true since 1.18.
     */
    private int lightColor(int level) {
        return level == 0 ? Theme.danger() : Theme.success();
    }
}
