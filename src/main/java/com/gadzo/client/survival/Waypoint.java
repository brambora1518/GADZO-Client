package com.gadzo.client.survival;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * A named place the player wants to find again.
 *
 * <p>Stored per world and per dimension. The dimension is part of the identity rather than a
 * display detail: the same coordinates mean different places in the Overworld and the Nether,
 * and a beam drawn through the wrong dimension would be worse than no beam at all.
 *
 * @param name       what the player called it
 * @param x          block coordinates
 * @param dimension  registry id of the dimension, e.g. {@code minecraft:overworld}
 * @param color      packed ARGB used for the beam and the label
 * @param visible    whether it is drawn in the world
 */
public record Waypoint(String name, int x, int y, int z, String dimension, int color,
                       boolean visible) {

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    /** Centre of the block, which is where a beam should stand. */
    public Vec3d center() {
        return new Vec3d(x + 0.5, y, z + 0.5);
    }

    public double distanceTo(Vec3d from) {
        return Math.sqrt(squaredDistanceTo(from));
    }

    /**
     * Horizontal distance only.
     *
     * <p>Used for the compass and the list, because on a survival map "how far is my base"
     * almost never means the 3D distance — a base 200 blocks away and 60 blocks up is 200
     * blocks of walking.
     */
    public double horizontalDistanceTo(Vec3d from) {
        double dx = (x + 0.5) - from.x;
        double dz = (z + 0.5) - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double squaredDistanceTo(Vec3d from) {
        double dx = (x + 0.5) - from.x;
        double dy = y - from.y;
        double dz = (z + 0.5) - from.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Compass bearing from a position, in degrees clockwise from south.
     *
     * <p>Matches Minecraft's own yaw convention so it can be compared with the player's facing
     * directly, without a correction term that would be easy to get backwards.
     */
    public double bearingFrom(Vec3d from) {
        double dx = (x + 0.5) - from.x;
        double dz = (z + 0.5) - from.z;
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    public Waypoint withVisible(boolean value) {
        return new Waypoint(name, x, y, z, dimension, color, value);
    }

    public Waypoint withColor(int value) {
        return new Waypoint(name, x, y, z, dimension, value, visible);
    }
}
