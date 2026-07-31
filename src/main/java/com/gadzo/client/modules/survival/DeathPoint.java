package com.gadzo.client.modules.survival;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.survival.Waypoint;
import com.gadzo.client.survival.WaypointStore;
import com.gadzo.client.ui.notify.Notifications;
import com.gadzo.client.util.Mc;

import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Records where you died, automatically.
 *
 * <p>The chat message vanilla prints on death gives coordinates only if the server has that
 * gamerule on, and it scrolls away in seconds. Losing an inventory because the coordinates
 * scrolled past is a bad way to lose an inventory.
 *
 * <p>Recorded as an ordinary waypoint, so the beam and the compass pick it up with no extra
 * work, and so it can be deleted the same way as any other.
 */
public class DeathPoint extends Module {

    private final BooleanSetting keepHistory;
    private final BooleanSetting announceOnRespawn;

    /**
     * Whether the current death has already been recorded.
     *
     * <p>Needed because the client sits on the death screen for as long as the player leaves it
     * there, ticking the whole time — without this the same death would be written every tick.
     */
    private boolean recordedThisDeath;

    /** Set after a death so the respawn message can be fired once the player is alive again. */
    private Waypoint pendingAnnouncement;

    public DeathPoint() {
        super("Death point", "Save a waypoint where you died", ModuleCategory.SURVIVAL);
        this.keepHistory = addBool("Keep history", false,
                "Number each death instead of overwriting the last one");
        this.announceOnRespawn = addBool("Announce on respawn", true,
                "Show the distance back to your things once you respawn");
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return;
        }

        if (player.getHealth() > 0 && !player.isDead()) {
            recordedThisDeath = false;
            announce(player);
            return;
        }
        if (recordedThisDeath) {
            return;
        }
        recordedThisDeath = true;
        record(player);
    }

    private void record(ClientPlayerEntity player) {
        String dimension = WaypointStore.currentDimension();
        if (dimension == null) {
            return;
        }
        String name = keepHistory.get() ? nextDeathName() : "Death";
        Waypoint waypoint = new Waypoint(name,
                (int) Math.floor(player.getX()),
                (int) Math.floor(player.getY()),
                (int) Math.floor(player.getZ()),
                dimension, WaypointStore.DEATH_COLOR, true);

        if (WaypointStore.add(waypoint)) {
            pendingAnnouncement = waypoint;
        }
    }

    /**
     * Fires the respawn message once, when the player is alive again.
     *
     * <p>Deferred rather than shown at the moment of death because the death screen covers the
     * whole window — a toast behind it would be gone by the time anyone could read it.
     */
    private void announce(ClientPlayerEntity player) {
        if (pendingAnnouncement == null) {
            return;
        }
        Waypoint waypoint = pendingAnnouncement;
        pendingAnnouncement = null;

        if (!announceOnRespawn.get()) {
            return;
        }
        boolean sameDimension = waypoint.dimension().equals(WaypointStore.currentDimension());
        String detail = sameDimension
                ? String.format("%d, %d, %d — %.0f m away", waypoint.x(), waypoint.y(),
                        waypoint.z(), waypoint.distanceTo(player.getPos()))
                : String.format("%d, %d, %d in another dimension", waypoint.x(), waypoint.y(),
                        waypoint.z());

        Notifications.warning("Death point saved", detail);
    }

    private String nextDeathName() {
        int index = 1;
        while (WaypointStore.byName("Death " + index) != null) {
            index++;
        }
        return "Death " + index;
    }
}
