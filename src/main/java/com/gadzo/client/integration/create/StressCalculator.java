package com.gadzo.client.integration.create;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a Create kinetic network: how much stress a set of machines draws, and what it takes
 * to power them.
 *
 * <p>Create's stress figures are per-RPM, which is what makes them counter-intuitive. On a
 * network running at a single speed, both consumption and capacity scale with that speed, so
 * gearing the whole thing up never buys headroom. The calculator therefore works in per-RPM
 * terms — the currency in which the comparison is actually decidable — and only converts to
 * absolute Stress Units where a figure needs to line up with what the goggles show.
 *
 * <p>When Create is installed the numbers come out of its own registry, so a pack that retunes
 * stress values, or a config edit, is reflected without this client knowing anything about it.
 * The built-in figures are a fallback for planning with Create absent; they are Create 6.0.8
 * defaults, read out of the mod rather than remembered.
 */
public final class StressCalculator {

    /**
     * A machine that consumes stress.
     *
     * @param name          display name
     * @param blockId       Create's block id, used to look up the live value
     * @param defaultImpact per-RPM draw when the live value is unavailable
     */
    public record Machine(String name, String blockId, double defaultImpact) {

        /** Live per-RPM impact when Create is present, otherwise the built-in default. */
        public double impact() {
            double live = CreateBridge.impactOf(block(blockId));
            return live >= 0 ? live : defaultImpact;
        }
    }

    /**
     * A generator, with its per-RPM capacity and the speed it turns at.
     *
     * <p>Generator speed is not a free choice — a water wheel turns at its own rate, and gearing
     * it changes what the machines see rather than what it supplies. Recording the native RPM is
     * what lets the planner convert between the per-RPM budget and the absolute SU figure a
     * player reads off a stressometer.
     */
    public record Generator(String name, String blockId, double defaultCapacity, int rpm,
                            String requirement) {

        public double capacityPerRpm() {
            double live = CreateBridge.capacityOf(block(blockId));
            return live > 0 ? live : defaultCapacity;
        }

        /** Absolute Stress Units this generator supplies while turning at its native speed. */
        public double totalStressUnits() {
            return capacityPerRpm() * rpm;
        }
    }

    /** One line of a plan: a machine and how many of it. */
    public static final class Line {
        private final Machine machine;
        private int count;

        Line(Machine machine, int count) {
            this.machine = machine;
            this.count = count;
        }

        public Machine machine() {
            return machine;
        }

        public int count() {
            return count;
        }

        public void adjust(int delta) {
            count = Math.max(0, count + delta);
        }

        public double totalImpact() {
            return machine.impact() * count;
        }
    }

    /**
     * Resolves a Create block id, or air when Create is not installed.
     *
     * <p>Returning air rather than throwing keeps every caller free of a null check; the bridge
     * treats it as an unknown block and the record falls back to its built-in figure.
     */
    private static Block block(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return Blocks.AIR;
        }
        Block resolved = Registries.BLOCK.get(new Identifier("create", blockId));
        return resolved == null ? Blocks.AIR : resolved;
    }

    /**
     * The machines offered by the planner.
     *
     * <p>Ordered roughly by how often they dominate a stress budget, so the expensive ones a
     * player most needs to plan around are near the top.
     */
    public static final List<Machine> MACHINES = List.of(
            new Machine("Mechanical Press", "mechanical_press", 8),
            new Machine("Crushing Wheel (each)", "crushing_wheel", 8),
            new Machine("Millstone", "millstone", 4),
            new Machine("Mechanical Mixer", "mechanical_mixer", 4),
            new Machine("Mechanical Saw", "mechanical_saw", 4),
            new Machine("Mechanical Drill", "mechanical_drill", 4),
            new Machine("Deployer", "deployer", 4),
            new Machine("Mechanical Pump", "mechanical_pump", 4),
            new Machine("Rope Pulley", "rope_pulley", 4),
            new Machine("Mechanical Bearing", "mechanical_bearing", 4),
            new Machine("Hose Pulley", "hose_pulley", 4),
            new Machine("Turntable", "turntable", 4),
            new Machine("Encased Fan", "encased_fan", 2),
            new Machine("Mechanical Arm", "mechanical_arm", 2),
            new Machine("Mechanical Crafter", "mechanical_crafter", 2),
            new Machine("Weighted Ejector", "weighted_ejector", 2),
            new Machine("Belt / Shaft / Gearbox", "", 0));

    /**
     * The generators offered by the planner.
     *
     * <p>The RPM column is the point of this table: a large water wheel supplies four times a
     * small one's capacity per RPM but turns at half the speed, so the honest comparison is the
     * product of the two, not either column on its own.
     */
    public static final List<Generator> GENERATORS = List.of(
            new Generator("Water Wheel", "water_wheel", 32, 8,
                    "Flowing water on the correct faces"),
            new Generator("Large Water Wheel", "large_water_wheel", 128, 4,
                    "Flowing water; far more output for the space"),
            new Generator("Windmill Bearing", "windmill_bearing", 512, 16,
                    "8 sails minimum, 8 sails per RPM, 16 RPM cap"),
            new Generator("Steam Engine", "steam_engine", 1024, 64,
                    "Output scales with boiler size, water and heat"),
            new Generator("Hand Crank", "hand_crank", 8, 32,
                    "Temporary, for testing a single machine"),
            new Generator("Creative Motor", "creative_motor", 16384, 256,
                    "Creative mode only"));

    private final List<Line> lines = new ArrayList<>();

    public List<Line> lines() {
        return lines;
    }

    /** Adds a machine, or increments it if already present. */
    public void add(Machine machine) {
        for (Line line : lines) {
            if (line.machine().equals(machine)) {
                line.adjust(1);
                return;
            }
        }
        lines.add(new Line(machine, 1));
    }

    public void remove(Machine machine) {
        lines.removeIf(line -> {
            if (!line.machine().equals(machine)) {
                return false;
            }
            line.adjust(-1);
            return line.count() == 0;
        });
    }

    public void clear() {
        lines.clear();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Total per-RPM impact of everything in the plan. */
    public double totalImpact() {
        double total = 0;
        for (Line line : lines) {
            total += line.totalImpact();
        }
        return total;
    }

    /** Total machine count, for the summary line. */
    public int totalMachines() {
        int total = 0;
        for (Line line : lines) {
            total += line.count();
        }
        return total;
    }

    /**
     * How many of a generator it takes to cover the plan.
     *
     * <p>Compared per-RPM, which is the correct comparison when the machines run at the
     * generator's own speed — the usual case, and the one where gearing has not been used to
     * decouple the two halves of the network.
     *
     * <p>Rounded up, because a network that is 0.4 generators short is simply overstressed.
     */
    public int generatorsNeeded(Generator generator) {
        double capacity = generator.capacityPerRpm();
        if (capacity <= 0) {
            return 0;
        }
        return (int) Math.ceil(totalImpact() / capacity);
    }

    /**
     * Absolute stress the plan draws at a given speed.
     *
     * <p>Provided so the figures line up with a stressometer, which reads absolute SU. The
     * ratio of used to available is unaffected by the speed chosen here.
     */
    public double stressAtSpeed(double rpm) {
        return totalImpact() * rpm;
    }

    /** Whether the figures on screen came from Create itself rather than the fallback table. */
    public static boolean usingLiveValues() {
        return CreateBridge.isLive();
    }
}
