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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * Altitude, speed and firework count while gliding.
 *
 * <p>None of this is on screen anywhere in vanilla. The two numbers that matter most in a long
 * glide are the vertical speed — whether you are still climbing off a firework or already
 * sinking — and how many rockets are left, because running out mid-flight over the wrong
 * terrain is one of the more common needless elytra deaths.
 *
 * <p>Only visible while actually gliding, so it costs nothing the rest of the time and never
 * competes for space with the HUD elements someone actually watches on the ground.
 */
public class ElytraHud extends HudModule {

    private static final double WIDTH = 108.0;

    private final BooleanSetting showAltitude;
    private final BooleanSetting showHorizontalSpeed;
    private final BooleanSetting showRockets;

    public ElytraHud() {
        super("Elytra", "Altitude, speed and rockets while gliding", ModuleCategory.SURVIVAL,
                HudAnchor.MIDDLE_RIGHT, -6, 0);
        this.showAltitude = addBool("Altitude", true, "Height above the world's bottom");
        this.showHorizontalSpeed = addBool("Horizontal speed", true,
                "Ground speed, alongside the vertical rate");
        this.showRockets = addBool("Rockets", true, "Firework rockets left in your inventory");
    }

    private boolean gliding() {
        ClientPlayerEntity player = Mc.player();
        return player != null && player.isFallFlying();
    }

    private int rows() {
        int rows = 1; // vertical speed, always shown
        if (showAltitude.get()) rows++;
        if (showHorizontalSpeed.get()) rows++;
        if (showRockets.get()) rows++;
        return rows;
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return gliding() ? WIDTH : 0;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return gliding() ? rows() * (font.fontHeight + 2) - 2 : 0;
    }

    private int rocketCount(ClientPlayerEntity player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        ClientPlayerEntity player = Mc.player();
        if (player == null || !gliding()) {
            return;
        }
        Vec3d velocity = player.getVelocity();
        // Velocity is per-tick; the familiar blocks-per-second figure is twenty times that.
        double verticalSpeed = velocity.y * 20;
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20;

        double y = 0;
        double rowHeight = font.fontHeight + 2;

        String vertical = String.format("%+.1f m/s", verticalSpeed);
        line(gfx, font, "Vertical", 0, y, Theme.textSecondary());
        Render2D.textRight(gfx, font, vertical, WIDTH, y,
                verticalSpeed >= 0 ? Theme.success() : Theme.warning(), hasTextShadow());
        y += rowHeight;

        if (showHorizontalSpeed.get()) {
            line(gfx, font, "Speed", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font, String.format("%.1f m/s", horizontalSpeed), WIDTH, y,
                    Theme.textPrimary(), hasTextShadow());
            y += rowHeight;
        }

        if (showAltitude.get()) {
            line(gfx, font, "Altitude", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font, Math.round(player.getY() - player.getWorld().getBottomY())
                    + " m", WIDTH, y, Theme.textPrimary(), hasTextShadow());
            y += rowHeight;
        }

        if (showRockets.get()) {
            int rockets = rocketCount(player);
            line(gfx, font, "Rockets", 0, y, Theme.textSecondary());
            Render2D.textRight(gfx, font, Integer.toString(rockets), WIDTH, y,
                    rockets == 0 ? Theme.danger() : rockets < 3 ? Theme.warning() : Theme.success(),
                    hasTextShadow());
        }
    }
}
