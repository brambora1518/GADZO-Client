package com.gadzo.client.integration.create;

import com.gadzo.client.util.Mc;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds every distinct Create kinetic network within range of the player.
 *
 * <p>The Kinetic readout HUD element shows one block at a time — whatever is under the
 * crosshair. This answers a different question: "how is my whole factory doing", by walking
 * every loaded chunk in range, grouping the kinetic blocks it finds by their network id, and
 * summing each network's stress and capacity once (every member reports the same network
 * totals, so it takes only the first reading per network rather than adding them up).
 *
 * <p>Deliberately not run every frame. Walking every block entity in every loaded chunk in a
 * useful radius is real work, and a client-side mod causing the exact stutter it exists to
 * help diagnose would be a bad joke. Callers are expected to throttle: the Create helper
 * screen re-scans on a timer while its tab is open and otherwise not at all.
 */
public final class NetworkScanner {

    /** Chunks (16-block columns) scanned outward from the player on each axis. */
    public static final int SCAN_RADIUS_CHUNKS = 4;

    /** A network's summary: its id, member count, and the stress numbers every member agrees on. */
    public record NetworkInfo(long id, int memberCount, float stress, float capacity,
                              boolean overStressed, BlockPos closestMember) {

        public double load() {
            return capacity <= 0 ? (stress > 0 ? Double.POSITIVE_INFINITY : 0) : stress / capacity;
        }
    }

    private NetworkScanner() {
    }

    /**
     * Scans loaded chunks around the player and returns every distinct network found.
     *
     * <p>Only chunks already loaded on the client are read — {@code load=false} on the chunk
     * lookup — so this cannot force a chunk to generate or trigger any network traffic; it is
     * strictly reading data the client already has for rendering.
     */
    public static List<NetworkInfo> scanNearby() {
        if (!CreateBridge.isLive()) {
            return List.of();
        }
        PlayerEntity player = Mc.player();
        ClientWorld world = Mc.client() == null ? null : Mc.client().world;
        if (player == null || world == null) {
            return List.of();
        }

        // id -> accumulated info, built with a single reading per network (see class docs).
        Map<Long, Accumulator> byNetwork = new LinkedHashMap<>();

        ChunkPos center = new ChunkPos(player.getBlockPos());
        for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                if (!(world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false)
                        instanceof WorldChunk chunk)) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockEntity blockEntity = entry.getValue();
                    if (!CreateBridge.isKinetic(blockEntity)) {
                        continue;
                    }
                    Long networkId = CreateBridge.networkId(blockEntity);
                    if (networkId == null) {
                        continue;
                    }
                    KineticReading reading = CreateBridge.read(blockEntity, "");
                    if (reading == null) {
                        continue;
                    }
                    Accumulator acc = byNetwork.computeIfAbsent(networkId, Accumulator::new);
                    acc.add(entry.getKey(), reading, player.getPos());
                }
            }
        }

        List<NetworkInfo> result = new ArrayList<>(byNetwork.size());
        for (Accumulator acc : byNetwork.values()) {
            result.add(acc.toInfo());
        }
        result.sort((a, b) -> Float.compare(b.stress(), a.stress()));
        return result;
    }

    /** Builds one {@link NetworkInfo} while walking the scan, tracking the closest member seen. */
    private static final class Accumulator {
        private final long id;
        private int members;
        private float stress;
        private float capacity;
        private boolean overStressed;
        private BlockPos closest;
        private double closestDistanceSquared = Double.MAX_VALUE;

        Accumulator(long id) {
            this.id = id;
        }

        void add(BlockPos pos, KineticReading reading, net.minecraft.util.math.Vec3d playerPos) {
            members++;
            // Every member reports the same network-wide stress and capacity, so taking the
            // first is correct; taking every member's would multiply the total by network size.
            if (members == 1) {
                stress = reading.stress();
                capacity = reading.capacity();
            }
            if (reading.overStressed()) {
                overStressed = true;
            }
            double distanceSquared = pos.getSquaredDistance(playerPos);
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closest = pos;
            }
        }

        NetworkInfo toInfo() {
            return new NetworkInfo(id, members, stress, capacity, overStressed, closest);
        }
    }
}
