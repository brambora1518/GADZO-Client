package com.gadzo.client.modules.create;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.integration.create.CreateBridge;
import com.gadzo.client.integration.create.KineticReading;
import com.gadzo.client.ui.notify.Notifications;
import com.gadzo.client.util.Mc;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Warns before a kinetic network stalls, rather than after.
 *
 * <p>Overstressing is loud once it happens — everything stops and the stressometer goes red —
 * but by then the machine has already stopped mid-batch. The useful moment is the one before:
 * a network sitting at 90% has room for nothing, and adding the next press will take it down.
 *
 * <p>Notifications are keyed to the block position and rate-limited, because a network is many
 * blocks and looking along a belt would otherwise fire the same warning a dozen times. Moving
 * to a different network re-arms it immediately, since that genuinely is new information.
 */
public class StressAlert extends Module {

    /** Ignore the first ticks in a world; block entities have not synced their network yet. */
    private static final int SETTLE_TICKS = 60;

    private final NumberSetting threshold;
    private final BooleanSetting warnOverstressed;
    private final BooleanSetting warnSpeed;
    private final NumberSetting cooldown;

    private BlockPos lastWarnedPos;
    private String lastWarnedKind = "";
    private long lastWarnedAt;
    private int ticksInWorld;

    public StressAlert() {
        super("Stress alert", "Warn when a Create network is close to stalling",
                ModuleCategory.CREATE);
        this.threshold = addNumber("Warn above", 90, 50, 100, 5,
                "Network load that triggers a warning")
                .suffix("%");
        this.warnOverstressed = addBool("Overstressed", true,
                "Also warn when a network has already stalled");
        this.warnSpeed = addBool("Too slow", true,
                "Warn when a machine is turning too slowly to work");
        this.cooldown = addNumber("Cooldown", 20, 5, 120, 5,
                "Minimum gap between warnings about the same network")
                .suffix(" s");
    }

    @Override
    protected void onEnable() {
        ticksInWorld = 0;
        lastWarnedPos = null;
    }

    @Override
    public void onTick() {
        if (!inGame() || !CreateBridge.isLive()) {
            ticksInWorld = 0;
            return;
        }
        if (ticksInWorld < SETTLE_TICKS) {
            ticksInWorld++;
            return;
        }

        MinecraftClient client = Mc.client();
        if (client == null || client.world == null
                || !(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockEntity blockEntity = client.world.getBlockEntity(pos);
        if (!CreateBridge.isKinetic(blockEntity)) {
            return;
        }

        String name = blockEntity.getCachedState().getBlock().getName().getString();
        KineticReading reading = CreateBridge.read(blockEntity, name);
        if (reading == null || !reading.connected()) {
            return;
        }

        if (reading.overStressed() && warnOverstressed.get()) {
            warn(pos, "overstressed", "Overstressed",
                    String.format("%s needs %.0f su but the network supplies %.0f.",
                            name, reading.stress(), reading.capacity()));
            return;
        }

        double load = reading.load();
        if (load * 100 >= threshold.get() && load <= 1.0) {
            warn(pos, "load", "Network nearly full",
                    String.format("%.0f%% of %.0f su used — %.0f su of headroom left.",
                            load * 100, reading.capacity(), reading.headroom()));
            return;
        }

        if (warnSpeed.get() && !reading.speedRequirementMet() && !reading.stopped()) {
            warn(pos, "speed", "Too slow",
                    String.format("%s is turning at %.0f RPM, below what it needs. Gear up with "
                            + "a large cogwheel driving a small one.", name, reading.absoluteSpeed()));
        }
    }

    /**
     * Fires a warning unless it repeats one the player has just seen.
     *
     * <p>A different position on the same network still counts as the same warning while the
     * cooldown is running; a different kind of problem does not, because "overstressed" and
     * "too slow" need different fixes.
     */
    private void warn(BlockPos pos, String kind, String title, String message) {
        long now = System.currentTimeMillis();
        boolean sameProblem = kind.equals(lastWarnedKind);
        boolean withinCooldown = now - lastWarnedAt < cooldown.get() * 1000;

        if (sameProblem && withinCooldown) {
            return;
        }
        lastWarnedPos = pos;
        lastWarnedKind = kind;
        lastWarnedAt = now;
        Notifications.warning(title, message);
    }

    /** Exposed for the helper screen, which shows what the last warning was about. */
    public BlockPos lastWarnedPosition() {
        return lastWarnedPos;
    }
}
