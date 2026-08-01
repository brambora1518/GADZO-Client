package com.gadzo.client.modules.performance;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.integration.create.CreateBridge;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Stops drawing block entities that are too far away to be worth the frame time.
 *
 * <p>On a Create pack this is the largest single frame-time saving available. Every shaft,
 * cogwheel, belt, funnel and pipe is a block entity with an animated renderer that rebuilds
 * its model every frame, and a mid-sized factory holds hundreds of them. Vanilla applies no
 * distance limit of its own, so a base stays fully animated from across the valley whether or
 * not anyone can make out what it is doing.
 *
 * <p>Kinetic blocks get their own, shorter limit. They are both the most numerous and the most
 * expensive to draw, and unlike a chest or a sign they carry no information at distance — a
 * cog spinning 80 blocks away tells the player nothing that the same cog at 40 blocks does not.
 *
 * <p>Beacons are exempt by default. Their beam is drawn from the block entity renderer and is
 * a navigation landmark specifically because it is visible from far away; culling it would be
 * removing a feature rather than an expense.
 */
public class BlockEntityLimiter extends Module {

    private static BlockEntityLimiter instance;

    private final NumberSetting distance;
    private final BooleanSetting limitKinetics;
    private final NumberSetting kineticDistance;
    private final BooleanSetting keepBeacons;

    public BlockEntityLimiter() {
        super("Block entity limit", "Cap how far away chests, signs and Create machines are drawn",
                ModuleCategory.PERFORMANCE);
        instance = this;

        this.distance = addNumber("Distance", 64, 16, 256, 4,
                "Block entities beyond this many blocks are not drawn")
                .suffix("m");
        this.limitKinetics = addBool("Shorter limit for Create", true,
                "Kinetic blocks are the most numerous and the most expensive to animate");
        this.kineticDistance = add(new NumberSetting("Create distance", 40, 8, 160, 4)
                .suffix("m")
                .<NumberSetting>describe("Distance limit applied to Create kinetic blocks")
                .visibleWhen(() -> this.limitKinetics.get()));
        this.keepBeacons = addBool("Always show beacons", true,
                "Beacon beams are landmarks — never cull them");
    }

    public static BlockEntityLimiter get() {
        return instance;
    }

    /**
     * Whether this block entity should be skipped for this frame.
     *
     * <p>Runs for every visible block entity every frame, so it is allocation-free and ordered
     * cheapest test first. The kinetic check is last because it is the only one that touches
     * the Create bridge.
     */
    public boolean shouldSkip(BlockEntity blockEntity, Camera camera) {
        if (!isEnabled() || blockEntity == null || camera == null) {
            return false;
        }
        if (keepBeacons.get() && blockEntity instanceof BeaconBlockEntity) {
            return false;
        }

        Vec3d eye = camera.getPos();
        BlockPos pos = blockEntity.getPos();
        double dx = pos.getX() + 0.5 - eye.x;
        double dy = pos.getY() + 0.5 - eye.y;
        double dz = pos.getZ() + 0.5 - eye.z;
        double squared = dx * dx + dy * dy + dz * dz;

        double limit = distance.get();
        if (limitKinetics.get()) {
            double kinetic = kineticDistance.get();
            // Only worth asking Create when the block is already past the shorter limit.
            if (squared > kinetic * kinetic && CreateBridge.isKinetic(blockEntity)) {
                limit = Math.min(limit, kinetic);
            }
        }

        return squared > limit * limit;
    }
}
