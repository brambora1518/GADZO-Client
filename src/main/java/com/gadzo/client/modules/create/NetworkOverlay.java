package com.gadzo.client.modules.create;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.integration.create.CreateBridge;
import com.gadzo.client.integration.create.KineticReading;
import com.gadzo.client.integration.create.NetworkScanner;
import com.gadzo.client.util.Mc;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Outlines every block sharing a kinetic network with the one you are looking at.
 *
 * <p>Create gives no way to see the shape of a network. A shaft that is not turning is either
 * disconnected or overstressed, and telling those apart means following the drive by eye
 * through walls, floors and encasing until you find where it stops — which on a real factory
 * can take longer than building the thing did.
 *
 * <p>Look at any kinetic block and the whole network it belongs to lights up. Where the
 * highlight ends is where the connection ends, and that is the answer to nearly every "why is
 * this not spinning" question in the mod.
 *
 * <p>The outline is red while the network is overstressed. That reuses the one piece of state
 * every member already agrees on, so the colour is a property of the network rather than of
 * whichever block happened to be under the crosshair.
 */
public class NetworkOverlay extends Module {

    /**
     * How often the member list is rebuilt.
     *
     * <p>The scan walks the block entities of every loaded chunk in range, so it is on a timer.
     * Network membership only changes when someone places or breaks a shaft, and a fifth of a
     * second late on that is imperceptible; running it per frame would be a diagnostic tool
     * that costs more frame time than the problem it diagnoses.
     */
    private static final long RESCAN_MILLIS = 500;

    private final NumberSetting maxDistance;
    private final NumberSetting opacity;
    private final BooleanSetting colorByStress;
    private final BooleanSetting holdOnLookAway;

    private List<BlockPos> members = List.of();
    private long networkId;
    private boolean haveNetwork;
    private boolean overStressed;
    private long lastScanAt;
    private long lastSeenAt;

    public NetworkOverlay() {
        super("Network overlay", "Outline every block on the kinetic network you are looking at",
                ModuleCategory.CREATE);

        this.maxDistance = addNumber("Max distance", 48, 8, 128, 4,
                "Stop outlining members further away than this")
                .suffix("m");
        this.colorByStress = addBool("Colour by stress", true,
                "Red while the network is overstressed");
        this.holdOnLookAway = addBool("Hold briefly", true,
                "Keep the outline for a moment after looking away, so you can walk the drive");
        this.opacity = addNumber("Opacity", 0.7, 0.1, 1.0, 0.05, "How solid the outline is");
    }

    /** Wires the world-render hook. Called once at startup. */
    public static void register(NetworkOverlay module) {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (module.isEnabled()) {
                module.renderWorld(context);
            }
        });
    }

    @Override
    protected void onDisable() {
        members = List.of();
        haveNetwork = false;
    }

    @Override
    public void onTick() {
        if (!CreateBridge.isLive()) {
            return;
        }
        MinecraftClient client = Mc.client();
        if (client == null || client.world == null) {
            return;
        }

        long now = System.currentTimeMillis();

        // Which network is under the crosshair right now.
        if (client.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            BlockEntity blockEntity = client.world.getBlockEntity(hit.getBlockPos());
            if (CreateBridge.isKinetic(blockEntity)) {
                Long id = CreateBridge.networkId(blockEntity);
                if (id != null) {
                    KineticReading reading = CreateBridge.read(blockEntity, "");
                    overStressed = reading != null && reading.overStressed();
                    lastSeenAt = now;
                    if (!haveNetwork || id != networkId) {
                        networkId = id;
                        haveNetwork = true;
                        lastScanAt = 0L;
                    }
                }
            }
        }

        if (!haveNetwork) {
            return;
        }
        // Looking away drops the highlight, after a grace period if one was asked for.
        long grace = holdOnLookAway.get() ? 1500L : 150L;
        if (now - lastSeenAt > grace) {
            haveNetwork = false;
            members = List.of();
            return;
        }
        if (now - lastScanAt >= RESCAN_MILLIS) {
            lastScanAt = now;
            members = NetworkScanner.membersOf(networkId);
        }
    }

    private void renderWorld(WorldRenderContext context) {
        List<BlockPos> current = members;
        if (current.isEmpty()) {
            return;
        }
        Camera camera = context.camera();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null || camera == null) {
            return;
        }

        Vec3d eye = camera.getPos();
        double limit = maxDistance.get();
        double limitSquared = limit * limit;
        float alpha = (float) opacity.get();

        boolean stressed = colorByStress.get() && overStressed;
        float red = stressed ? 0.96f : 0.36f;
        float green = stressed ? 0.27f : 0.75f;
        float blue = stressed ? 0.36f : 1.0f;

        matrices.push();
        matrices.translate(-eye.x, -eye.y, -eye.z);

        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        for (BlockPos pos : current) {
            if (pos.getSquaredDistance(eye) > limitSquared) {
                continue;
            }
            // Inset slightly so the outline sits inside the block face rather than z-fighting
            // with the block next to it — kinetic blocks are usually in unbroken runs.
            WorldRenderer.drawBox(matrices, lines,
                    pos.getX() + 0.02, pos.getY() + 0.02, pos.getZ() + 0.02,
                    pos.getX() + 0.98, pos.getY() + 0.98, pos.getZ() + 0.98,
                    red, green, blue, alpha);
        }

        matrices.pop();
        // Not flushed here on purpose — see Waypoints.renderWorld for why.
    }
}
