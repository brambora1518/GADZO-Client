package com.gadzo.client.modules.create;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.integration.create.CreateBridge;
import com.gadzo.client.integration.create.KineticReading;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Live stress and speed for the Create machine you are looking at.
 *
 * <p>This is the Engineer's Goggles readout without the goggles, and with the numbers laid out
 * for diagnosis rather than for a tooltip: speed, network load as a bar, and a single sentence
 * naming the problem when there is one.
 *
 * <p>All of it is data the client already has. Create syncs a network's stress, capacity and
 * size to every client that can see one of its blocks, because the goggles and the stressometer
 * are client-rendered — this module reads the same fields they do.
 *
 * <p>The reading is held for a grace period after the block leaves the crosshair. Diagnosing a
 * contraption means walking along it, and a panel that vanished the moment you looked away
 * would be unreadable exactly when you needed it.
 */
public class KineticHud extends HudModule {

    private static final double WIDTH = 150.0;
    private static final double BAR_HEIGHT = 5.0;

    private final NumberSetting holdTime;
    private final BooleanSetting showNetworkSize;
    private final BooleanSetting showImpact;
    private final BooleanSetting showDiagnosis;
    private final BooleanSetting hideWhenIdle;

    /** Smooths the load bar so a machine kicking in animates instead of snapping. */
    private final Animation loadBar = new Animation(0.0, 240L);

    private KineticReading reading;
    private long readingTakenAt;

    public KineticHud() {
        super("Kinetic readout", "Live speed and stress for the Create block you are looking at",
                ModuleCategory.CREATE, HudAnchor.MIDDLE_CENTER, 0, 60);
        this.holdTime = addNumber("Hold time", 3.0, 0.5, 15.0, 0.5,
                "Keep the last reading this long after looking away")
                .suffix(" s");
        this.showNetworkSize = addBool("Network size", true, "Number of blocks in the network");
        this.showImpact = addBool("Block impact", true, "This block's own stress draw, per RPM");
        this.showDiagnosis = addBool("Diagnosis", true, "One line naming the problem, if any");
        this.hideWhenIdle = addBool("Hide when idle", true,
                "Only appear while a kinetic block is or was recently in view");
    }

    // -- data ----------------------------------------------------------------------------

    /**
     * Refreshes the held reading from whatever is under the crosshair.
     *
     * <p>Deliberately re-reads every frame rather than caching per block position: stress and
     * speed change while you watch, and a stale number is worse than no number on a readout
     * whose whole job is to tell you what a network is doing right now.
     *
     * <p>The lookup itself is throttled to {@value #REFRESH_MILLIS} ms rather than run every
     * frame: it is a raycast plus several reflective method calls into Create, and stress or
     * RPM on a real contraption does not change fast enough for a viewer to notice the
     * difference between that and a 240 Hz refresh — only the allocation and reflection
     * overhead would.
     */
    private static final long REFRESH_MILLIS = 100;
    private long lastRefreshAt;

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshAt < REFRESH_MILLIS) {
            return;
        }
        lastRefreshAt = now;

        MinecraftClient client = Mc.client();
        if (client == null || client.world == null) {
            return;
        }
        if (client.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockEntity blockEntity = client.world.getBlockEntity(pos);
            if (CreateBridge.isKinetic(blockEntity)) {
                String name = blockEntity.getCachedState().getBlock().getName().getString();
                KineticReading fresh = CreateBridge.read(blockEntity, name);
                if (fresh != null) {
                    reading = fresh;
                    readingTakenAt = now;
                    return;
                }
            }
        }
        if (reading != null && now - readingTakenAt > holdTime.get() * 1000) {
            reading = null;
        }
    }

    private boolean visible() {
        if (!CreateBridge.isLive()) {
            return false;
        }
        return reading != null || !hideWhenIdle.get();
    }

    private int rows() {
        int rows = 2; // name, speed
        if (showNetworkSize.get()) rows++;
        if (showImpact.get()) rows++;
        if (showDiagnosis.get()) rows++;
        return rows;
    }

    // -- geometry ------------------------------------------------------------------------

    @Override
    public double contentWidth(TextRenderer font) {
        return visible() ? WIDTH : 0;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        if (!visible()) {
            return 0;
        }
        return rows() * (font.fontHeight + 2) + BAR_HEIGHT + 4;
    }

    // -- rendering -----------------------------------------------------------------------

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        refresh();
        if (!visible()) {
            return;
        }
        if (reading == null) {
            Render2D.text(gfx, font, "Look at a kinetic block", 0, 0, Theme.textMuted());
            return;
        }

        double y = 0;
        double rowHeight = font.fontHeight + 2;

        line(gfx, font, Render2D.truncate(font, reading.blockName(), (int) (WIDTH - 40)), 0, y,
                Theme.textPrimary());
        Render2D.textRight(gfx, font, reading.tier().label(), WIDTH, y, speedColor(), hasTextShadow());
        y += rowHeight;

        // Speed, with the direction spelled out. Create's sign convention is not something
        // people carry in their heads, and a reversed shaft is a common cause of a machine
        // running the wrong way round.
        String speed = String.format("%.0f RPM", reading.absoluteSpeed());
        if (!reading.stopped()) {
            speed += reading.reversed() ? "  ↺" : "  ↻";
        }
        line(gfx, font, speed, 0, y, speedColor());
        y += rowHeight;

        drawLoadBar(gfx, y);
        y += BAR_HEIGHT + 4;

        if (showNetworkSize.get()) {
            String stress = String.format("%.0f / %.0f su", reading.stress(), reading.capacity());
            line(gfx, font, stress, 0, y, loadColor());
            Render2D.textRight(gfx, font, reading.networkSize() + " blocks", WIDTH, y,
                    Theme.textMuted(), hasTextShadow());
            y += rowHeight;
        }

        if (showImpact.get()) {
            String own = reading.isGenerator()
                    ? String.format("Supplies %.0f su/rpm", reading.capacityPerRpm())
                    : String.format("Draws %.0f su/rpm", reading.impactPerRpm());
            line(gfx, font, own, 0, y, Theme.textSecondary());
            y += rowHeight;
        }

        if (showDiagnosis.get()) {
            line(gfx, font, Render2D.truncate(font, reading.diagnosis(), (int) WIDTH), 0, y,
                    reading.isProblem() ? Theme.danger() : Theme.success());
        }
    }

    private void drawLoadBar(DrawContext gfx, double y) {
        double load = MathUtil.clamp(reading.load(), 0.0, 1.0);
        loadBar.to(load);

        Render2D.roundedRect(gfx, 0, y, WIDTH, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                ColorUtil.withAlpha(Theme.trackOff(), 190));
        double filled = WIDTH * loadBar.value();
        if (filled > 0.5) {
            Render2D.roundedRect(gfx, 0, y, filled, BAR_HEIGHT, BAR_HEIGHT / 2.0, loadColor());
        }
    }

    /**
     * Colour for the load bar, on Create's own thresholds.
     *
     * <p>The amber band starts at 75% rather than at the point of failure, because a network
     * at 90% is one machine away from stalling and that is the moment worth flagging.
     */
    private int loadColor() {
        if (reading.overStressed()) {
            return Theme.danger();
        }
        double load = reading.load();
        if (load > 0.9) return Theme.danger();
        if (load > 0.75) return Theme.warning();
        return Theme.success();
    }

    private int speedColor() {
        return switch (reading.tier()) {
            case STOPPED -> Theme.danger();
            case SLOW -> Theme.warning();
            case MEDIUM -> Theme.textPrimary();
            case FAST -> Theme.accent();
        };
    }
}
