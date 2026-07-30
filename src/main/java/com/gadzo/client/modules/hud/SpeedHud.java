package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Horizontal movement speed in blocks per second.
 *
 * <p>Derived from the per-tick position delta rather than from the velocity field, which on
 * the client is frequently zero for other-entity-driven movement (boats, ice, pistons) and
 * would under-report.
 */
public class SpeedHud extends HudModule {

    /** MinecraftClient runs 20 ticks per second. */
    private static final double TPS = 20.0;

    private final BooleanSetting includeVertical;
    private final BooleanSetting smooth;

    private double lastX;
    private double lastZ;
    private double lastY;
    private double smoothed;
    private boolean primed;

    public SpeedHud() {
        super("Speed", "Movement speed in blocks per second", HudAnchor.BOTTOM_LEFT, 4, -20);
        this.includeVertical = addBool("Include vertical", false,
                "Count falling and flying speed as well as horizontal movement");
        this.smooth = addBool("Smooth", true, "Average the reading so it does not jitter");
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            primed = false;
            return;
        }
        if (!primed) {
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();
            primed = true;
            return;
        }

        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        double dy = player.getY() - lastY;

        double distance = includeVertical.get()
                ? Math.sqrt(dx * dx + dy * dy + dz * dz)
                : Math.sqrt(dx * dx + dz * dz);

        double instant = distance * TPS;
        // Exponential moving average; a plain per-tick reading flickers too much to read.
        smoothed = smooth.get() ? smoothed + (instant - smoothed) * 0.25 : instant;

        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
    }

    private String text() {
        return String.format("%.2f m/s", smoothed);
    }

    @Override
    public double contentWidth(TextRenderer font) {
        // Reserve a fixed width so the plate does not resize while running.
        return font.getWidth("00.00 m/s");
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
