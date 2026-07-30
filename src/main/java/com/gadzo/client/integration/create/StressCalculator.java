package com.gadzo.client.integration.create;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a Create kinetic network: how much stress a set of machines draws, and what it takes
 * to power them.
 *
 * <p>Create's stress figures are per-RPM, which is what makes them counter-intuitive. Both
 * consumption and capacity scale with rotation speed, so running a network faster never buys
 * headroom. The calculator therefore reports the per-RPM balance — the number that actually
 * decides whether a build works — rather than a total that would silently depend on speed.
 *
 * <p>Impact and capacity values are Create 0.5.x figures and drift between releases. They are
 * planning estimates; the Engineer's Goggles are authoritative for a real contraption.
 */
public final class StressCalculator {

    /** A machine that consumes stress, with its per-RPM impact. */
    public record Machine(String name, double impact) {
    }

    /** A generator, with its per-RPM capacity and a note on what it needs to run. */
    public record Generator(String name, double capacity, String requirement) {
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
     * The machines offered by the planner.
     *
     * <p>Ordered roughly by how often they dominate a stress budget, so the expensive ones a
     * player most needs to plan around are near the top.
     */
    public static final List<Machine> MACHINES = List.of(
            new Machine("Mechanical Press", 8),
            new Machine("Crushing Wheels (pair)", 8),
            new Machine("Mechanical Mixer", 4),
            new Machine("Millstone", 4),
            new Machine("Mechanical Saw", 4),
            new Machine("Mechanical Drill", 4),
            new Machine("Deployer", 4),
            new Machine("Mechanical Piston", 4),
            new Machine("Rope Pulley", 4),
            new Machine("Mechanical Bearing", 4),
            new Machine("Encased Fan", 2),
            new Machine("Mechanical Arm", 2),
            new Machine("Mechanical Crafter", 2),
            new Machine("Mechanical Harvester", 2),
            new Machine("Mechanical Plough", 2),
            new Machine("Belt / Shaft", 0));

    /** The generators offered by the planner. */
    public static final List<Generator> GENERATORS = List.of(
            new Generator("Water Wheel", 16, "Needs flowing water on the correct faces"),
            new Generator("Large Water Wheel", 32, "Flowing water; more output per block used"),
            new Generator("Windmill (8 sails)", 8, "Minimum sails and clear sky; scales with sails"),
            new Generator("Windmill (max sails)", 64, "Build wide rather than adding a second one"),
            new Generator("Steam Engine (per engine)", 16, "Scales with boiler level, water and heat"),
            new Generator("Hand Crank", 8, "Temporary, for testing one machine"));

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
     * <p>Rounded up, because a network that is 0.4 generators short is simply overstressed.
     */
    public int generatorsNeeded(Generator generator) {
        if (generator.capacity() <= 0) {
            return 0;
        }
        return (int) Math.ceil(totalImpact() / generator.capacity());
    }

    /**
     * Actual stress numbers at a given speed.
     *
     * <p>Provided so the figures line up with what the goggles show in game, which are
     * absolute SU rather than per-RPM. The ratio of used to available is unaffected by speed.
     */
    public double stressAtSpeed(double rpm) {
        return totalImpact() * rpm;
    }
}
