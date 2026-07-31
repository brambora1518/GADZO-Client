package com.gadzo.client.survival;

/**
 * Converts between Overworld and Nether coordinates.
 *
 * <p>One block in the Nether is eight in the Overworld, which makes the Nether the fastest
 * transport in the game and the arithmetic the most common thing survival players do on paper.
 *
 * <p>The Y axis is deliberately not scaled. Portal linking ignores height in the ratio, and
 * dividing Y by eight is a mistake that puts people in the roof or the lava sea. The height a
 * portal wants is whatever is convenient in that dimension.
 */
public final class NetherCalculator {

    public static final int RATIO = 8;

    /** Radius, in Nether blocks, within which an existing portal is reused instead of built. */
    public static final int NETHER_SEARCH_RADIUS = 16;

    /** The same search radius on the Overworld side. */
    public static final int OVERWORLD_SEARCH_RADIUS = 128;

    private NetherCalculator() {
    }

    /** Overworld X or Z to its Nether equivalent. */
    public static int toNether(int overworld) {
        return Math.floorDiv(overworld, RATIO);
    }

    /** Nether X or Z to its Overworld equivalent. */
    public static int toOverworld(int nether) {
        return nether * RATIO;
    }

    /**
     * How far apart two Overworld portals must be to link to separate Nether portals.
     *
     * <p>Two Overworld portals closer together than this end up inside each other's search
     * radius in the Nether and share a destination — the classic "both my portals go to the same
     * place" problem. Building the Nether side by hand and linking it deliberately is the fix,
     * but knowing the distance avoids the situation entirely.
     */
    public static int minimumOverworldSeparation() {
        return OVERWORLD_SEARCH_RADIUS * 2 + 1;
    }

    /**
     * Whether two Overworld positions are far enough apart to link independently.
     *
     * <p>Compares on each axis separately, matching how the game's own portal search works: the
     * region is a box, not a sphere, so two portals 200 blocks apart diagonally can still be
     * within 128 on both axes.
     */
    public static boolean linksIndependently(int x1, int z1, int x2, int z2) {
        int netherDx = Math.abs(toNether(x1) - toNether(x2));
        int netherDz = Math.abs(toNether(z1) - toNether(z2));
        return netherDx > NETHER_SEARCH_RADIUS || netherDz > NETHER_SEARCH_RADIUS;
    }

    /**
     * Advice for a pair of coordinates the player is looking at.
     *
     * @param inNether whether the source coordinates are Nether ones
     */
    public static String describe(int x, int z, boolean inNether) {
        if (inNether) {
            return String.format("Nether %d, %d is Overworld %d, %d. Build the Overworld portal "
                            + "within %d blocks of that point or it will link somewhere else.",
                    x, z, toOverworld(x), toOverworld(z), OVERWORLD_SEARCH_RADIUS);
        }
        return String.format("Overworld %d, %d is Nether %d, %d. Build the Nether portal within "
                        + "%d blocks of that point, and light it yourself so the game does not "
                        + "place one for you.",
                x, z, toNether(x), toNether(z), NETHER_SEARCH_RADIUS);
    }
}
