package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Information about the entity you are looking at or last attacked.
 *
 * <p>The target is held for a short grace period after it leaves the crosshair, because in a
 * fight the camera rarely stays on the opponent — a panel that vanished the instant you
 * looked away would flicker constantly and be useless.
 */
public class TargetHud extends HudModule {

    private static final double WIDTH = 118.0;
    private static final double PORTRAIT = 26.0;
    private static final double BAR_HEIGHT = 4.0;

    private final NumberSetting holdTime;
    private final BooleanSetting showDistance;
    private final BooleanSetting showAbsorption;
    private final BooleanSetting hideWhenNone;

    /** Smooths the health bar so chip damage animates instead of snapping. */
    private final Animation healthBar = new Animation(1.0, 260L);

    private LivingEntity target;
    private long targetSeenAt;

    public TargetHud() {
        super("Target", "Health and distance of who you are fighting", ModuleCategory.COMBAT,
                HudAnchor.MIDDLE_CENTER, 0, 40);
        this.holdTime = addNumber("Hold time", 2.0, 0.5, 10.0, 0.5,
                "Keep showing the target this long after losing sight of it")
                .suffix(" s");
        this.showDistance = addBool("Distance", true, "Show how far away the target is");
        this.showAbsorption = addBool("Absorption", true, "Include golden hearts in the bar");
        this.hideWhenNone = addBool("Hide when idle", true, "Only appear when there is a target");
    }

    /** Updates the held target from the crosshair, if anything living is under it. */
    private void refreshTarget() {
        MinecraftClient client = Mc.client();
        if (client == null) {
            return;
        }
        Entity looked = client.targetedEntity;
        if (looked instanceof LivingEntity living && living != client.player && living.isAlive()) {
            target = living;
            targetSeenAt = System.currentTimeMillis();
            return;
        }
        // Drop the target once the grace period lapses or it dies.
        if (target != null) {
            boolean expired = System.currentTimeMillis() - targetSeenAt > holdTime.get() * 1000;
            if (expired || !target.isAlive() || target.isRemoved()) {
                target = null;
            }
        }
    }

    private boolean visible() {
        return target != null || !hideWhenNone.get();
    }

    private double healthFraction() {
        if (target == null) {
            return 0;
        }
        float max = target.getMaxHealth();
        if (max <= 0) {
            return 0;
        }
        float current = target.getHealth();
        if (showAbsorption.get()) {
            current += target.getAbsorptionAmount();
            max += target.getAbsorptionAmount();
        }
        return MathUtil.clamp(current / max, 0.0, 1.0);
    }

    private int healthColor(double fraction) {
        if (fraction > 0.5) return Theme.success();
        if (fraction > 0.25) return Theme.warning();
        return Theme.danger();
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return visible() ? WIDTH : 0;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return visible() ? PORTRAIT : 0;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        refreshTarget();
        if (!visible()) {
            return;
        }
        if (target == null) {
            Render2D.text(gfx, font, "No target", 0, (PORTRAIT - font.fontHeight) / 2.0,
                    Theme.textMuted());
            return;
        }

        double fraction = healthFraction();
        healthBar.to(fraction);

        String name = Render2D.truncate(font, target.getName().getString(), (int) (WIDTH - 46));
        line(gfx, font, name, 0, 1, Theme.textPrimary());

        // Health bar.
        double barY = font.fontHeight + 4;
        Render2D.roundedRect(gfx, 0, barY, WIDTH, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                ColorUtil.withAlpha(Theme.trackOff(), 190));
        double filled = WIDTH * healthBar.value();
        if (filled > 0.5) {
            Render2D.roundedRect(gfx, 0, barY, filled, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                    healthColor(fraction));
        }

        // Numbers under the bar.
        String health = String.format("%.1f / %.1f", target.getHealth(), target.getMaxHealth());
        line(gfx, font, health, 0, barY + BAR_HEIGHT + 2, Theme.textSecondary());

        if (showDistance.get() && Mc.player() != null) {
            String distance = String.format("%.1f m", Math.sqrt(target.squaredDistanceTo(Mc.player())));
            Render2D.textRight(gfx, font, distance, WIDTH, barY + BAR_HEIGHT + 2,
                    Theme.textMuted(), hasTextShadow());
        }
    }
}
