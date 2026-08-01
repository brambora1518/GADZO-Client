package com.gadzo.client.modules.survival;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.util.Mc;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks the blocks around you where hostile mobs can spawn.
 *
 * <p>Lighting a base is guesswork without this. The light level a torch actually reaches is not
 * something you can see, so the usual method is to place torches until it feels right and then
 * be surprised by a creeper in the corridor. This checks the rule the game itself uses and
 * draws a flat marker on every block that passes it.
 *
 * <p>Two colours, because there are two different problems. Red means a mob can spawn there
 * right now and will keep being able to: no block light and no sky light either. Amber means
 * the sky reaches it, so it is safe in daylight and dangerous after dusk — a roof or a torch
 * both fix it, and which one you want is a decision the marker cannot make for you.
 *
 * <p>The spawn test is the vanilla one: block light zero, a full solid face to stand on, and
 * two blocks of clear space above it. Anything looser would mark half the world; anything
 * stricter would miss the corner the spider actually came from.
 */
public class SpawnOverlay extends Module {

    /** Vertical reach either side of the player. Mobs spawn near you, not above the clouds. */
    private static final int VERTICAL_RADIUS = 8;

    /** How often the scan runs. Fast enough to follow a torch being placed. */
    private static final long RESCAN_MILLIS = 400;

    /** What a marker is. */
    private record Spot(BlockPos pos, boolean alwaysDark) {
    }

    private final NumberSetting radius;
    private final NumberSetting maxMarkers;
    private final BooleanSetting showNightOnly;
    private final NumberSetting opacity;

    private List<Spot> spots = List.of();
    private long lastScanAt;

    public SpawnOverlay() {
        super("Spawn overlay", "Show where hostile mobs can spawn around you",
                ModuleCategory.SURVIVAL);

        this.radius = addNumber("Radius", 16, 4, 32, 2,
                "How far to check on each horizontal axis")
                .suffix("m");
        this.showNightOnly = addBool("Include night-only spots", true,
                "Also mark blocks the sky reaches, which are only dangerous after dusk");
        this.maxMarkers = addNumber("Marker limit", 400, 50, 2000, 50,
                "Stop drawing past this many, so an unlit cave cannot cost you the frame rate");
        this.opacity = addNumber("Opacity", 0.55, 0.1, 1.0, 0.05,
                "How solid the markers are");
    }

    /** Wires the world-render hook. Called once at startup. */
    public static void register(SpawnOverlay module) {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (module.isEnabled()) {
                module.renderWorld(context);
            }
        });
    }

    @Override
    protected void onDisable() {
        spots = List.of();
        lastScanAt = 0L;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastScanAt < RESCAN_MILLIS) {
            return;
        }
        lastScanAt = now;
        spots = scan();
    }

    /**
     * Finds every spawnable block in range.
     *
     * <p>Runs on the client tick rather than per frame, and reuses one mutable position for the
     * whole walk — at a radius of 16 this visits about eighteen thousand blocks, and allocating
     * a {@code BlockPos} for each would hand the collector half a megabyte of garbage every
     * scan. That is precisely the kind of avoidable allocation that shows up later as the
     * stutter this client also ships a monitor for.
     */
    private List<Spot> scan() {
        MinecraftClient client = Mc.client();
        PlayerEntity player = Mc.player();
        ClientWorld world = client == null ? null : client.world;
        if (player == null || world == null) {
            return List.of();
        }

        int reach = (int) radius.get();
        int limit = (int) maxMarkers.get();
        boolean includeNightOnly = showNightOnly.get();

        BlockPos origin = player.getBlockPos();
        List<Spot> found = new ArrayList<>(Math.min(limit, 256));
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (world.getLightLevel(LightType.BLOCK, cursor) > 0) {
                        continue;
                    }
                    boolean alwaysDark = world.getLightLevel(LightType.SKY, cursor) == 0;
                    if (!alwaysDark && !includeNightOnly) {
                        continue;
                    }
                    if (!spawnable(world, cursor)) {
                        continue;
                    }
                    found.add(new Spot(cursor.toImmutable(), alwaysDark));
                    if (found.size() >= limit) {
                        return found;
                    }
                }
            }
        }
        return found;
    }

    /**
     * The vanilla spawn-position test: something solid underfoot and room to stand.
     *
     * <p>Checked in the order that rejects fastest — most positions in any scan are solid rock
     * or open air, and both fail on the first or second test.
     */
    private boolean spawnable(ClientWorld world, BlockPos pos) {
        BlockState here = world.getBlockState(pos);
        if (!here.getCollisionShape(world, pos).isEmpty()) {
            return false;
        }
        BlockPos below = pos.down();
        BlockState floor = world.getBlockState(below);
        if (!floor.isSideSolidFullSquare(world, below, Direction.UP)) {
            return false;
        }
        BlockPos above = pos.up();
        return world.getBlockState(above).getCollisionShape(world, above).isEmpty();
    }

    // -- rendering --------------------------------------------------------------------------

    private void renderWorld(WorldRenderContext context) {
        List<Spot> current = spots;
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
        float alpha = (float) opacity.get();

        matrices.push();
        matrices.translate(-eye.x, -eye.y, -eye.z);

        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        for (Spot spot : current) {
            BlockPos pos = spot.pos();
            // A flat square just clear of the floor, rather than a full cube: a cube around
            // every spawnable block in a dark room is an opaque wall of wireframe you cannot
            // see the room through, which defeats the point of standing in it with a torch.
            double y = pos.getY() + 0.015;
            if (spot.alwaysDark()) {
                WorldRenderer.drawBox(matrices, lines,
                        pos.getX() + 0.04, y, pos.getZ() + 0.04,
                        pos.getX() + 0.96, y, pos.getZ() + 0.96,
                        1.0f, 0.27f, 0.36f, alpha);
            } else {
                WorldRenderer.drawBox(matrices, lines,
                        pos.getX() + 0.16, y, pos.getZ() + 0.16,
                        pos.getX() + 0.84, y, pos.getZ() + 0.84,
                        0.96f, 0.65f, 0.14f, alpha * 0.8f);
            }
        }

        matrices.pop();
        // Not flushed here on purpose — see Waypoints.renderWorld for why.
    }
}
