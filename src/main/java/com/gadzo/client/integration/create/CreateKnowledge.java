package com.gadzo.client.integration.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reference material for the Create mod.
 *
 * <p>Create is not available for Minecraft 26.2 — the Forge/NeoForge build stops at 1.21.1 and
 * the Fabric port at 1.20.1 — so none of this can be read from a live kinetic network. It is a
 * static reference, useful for planning a contraption or remembering a ratio.
 *
 * <p>The content is deliberately weighted towards things that do not change between Create
 * versions: how stress arithmetic works, gear ratios, belt rules, and the diagnostic order for
 * a contraption that has stopped. Numeric stress values do drift between releases, so the ones
 * included are marked as 0.5.x figures and every entry that depends on them points at the
 * Engineer's Goggles, which are always the authority for the build actually in front of you.
 */
public final class CreateKnowledge {

    /** A reference entry: a heading, the body, and search keywords. */
    public record Entry(String title, List<String> body, String keywords) {

        public boolean matches(String needle) {
            if (needle.isEmpty()) {
                return true;
            }
            if (title.toLowerCase(Locale.ROOT).contains(needle)
                    || keywords.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            for (String paragraph : body) {
                if (paragraph.toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A group of entries, shown as one sidebar item. */
    public record Topic(String name, String summary, List<Entry> entries) {
    }

    private static final List<Topic> TOPICS = List.of(
            new Topic("Start here", "The two things that save the most time", List.of(
                    new Entry("Ponder is the real manual", List.of(
                            "Hover any Create item in your inventory or in JEI/EMI and press W.",
                            "Create ships an animated, step-by-step scene for almost every block "
                                    + "showing exactly how it is placed and wired up.",
                            "It is faster and more reliable than any wiki page, because it is "
                                    + "generated from the version you actually have installed."),
                            "ponder tutorial help guide w key manual wiki"),

                    new Entry("Wear the Engineer's Goggles", List.of(
                            "Goggles turn every kinetic block into a readout: current RPM, stress "
                                    + "used and stress capacity for the whole network.",
                            "Look at a block while wearing them and the numbers appear above your "
                                    + "hotbar; look at a generator and you see how much headroom is left.",
                            "Any stress figure in this reference is a planning estimate. The goggles "
                                    + "are the authority for the contraption in front of you."),
                            "goggles engineer stress rpm readout hud overlay"))),

            new Topic("Stress", "Why everything stopped, and how the arithmetic works", List.of(
                    new Entry("How stress actually works", List.of(
                            "Every machine has an impact value, and every generator a capacity "
                                    + "value. Both are per-RPM figures, not totals.",
                            "Stress used = impact x current RPM. Stress available = capacity x "
                                    + "current RPM.",
                            "Because both sides scale with RPM, running a network faster does NOT "
                                    + "help you power more machines — it raises consumption and "
                                    + "capacity by exactly the same factor.",
                            "This is the single most common misunderstanding. If you are "
                                    + "overstressed, you need more or better generators, or fewer "
                                    + "machines. Speeding things up changes nothing."),
                            "stress su overstressed capacity impact rpm formula maths"),

                    new Entry("Fixing an overstressed network", List.of(
                            "Symptom: everything shudders and stops, and a red stress bar flashes.",
                            "1. Add generators. Water wheels are cheap and stack well; a windmill "
                                    + "needs open sky and enough sails; steam is the endgame answer.",
                            "2. Split the network. Two independent water wheels driving half the "
                                    + "factory each is often easier than one large shared line.",
                            "3. Turn machines off when idle. A clutch or gearshift on a branch "
                                    + "removes its whole stress draw while disengaged.",
                            "4. Check for something you forgot — a stray drill or a deployer left "
                                    + "running in a corner is a classic cause."),
                            "overstressed red bar stopped shaking fix stress troubleshoot"),

                    new Entry("Typical impact values (Create 0.5.x)", List.of(
                            "Roughly, per unit of RPM. Verify with goggles — these drift between "
                                    + "versions.",
                            "Very light: belts and shafts cost nothing. Encased fan, mechanical "
                                    + "arm, harvester and plough are at the low end.",
                            "Moderate: millstone, mixer, saw, drill, deployer, mechanical piston, "
                                    + "rope pulley and bearings.",
                            "Heavy: mechanical press and crushing wheels are among the most "
                                    + "expensive things you can put on a line.",
                            "Practical consequence: a bank of presses or crushing wheels will "
                                    + "dominate your stress budget, so plan generation around those "
                                    + "first and treat everything else as rounding."),
                            "impact values su cost press crushing wheels mixer millstone list"))),

            new Topic("Speed", "Insufficient speed, and what needs how much", List.of(
                    new Entry("Speed is separate from stress", List.of(
                            "A machine can have plenty of stress available and still refuse to run "
                                    + "because it is turning too slowly.",
                            "Create labels rotation in tiers, and machines state a minimum. If a "
                                    + "block says the speed is insufficient, the fix is gearing, not "
                                    + "more power.",
                            "The maximum rotation speed anywhere in a network is 256 RPM. Exceed it "
                                    + "and the network breaks rather than going faster."),
                            "insufficient speed rpm slow minimum tier 256 limit"),

                    new Entry("Raising and lowering speed", List.of(
                            "A large cogwheel meshed with a small cogwheel changes speed by a factor "
                                    + "of two.",
                            "Large driving small doubles the speed and halves the torque budget; "
                                    + "small driving large does the reverse.",
                            "Chain several pairs for larger ratios: three steps up is eight times "
                                    + "the input speed.",
                            "The Rotation Speed Controller sets an exact RPM on a large cogwheel, "
                                    + "which is far easier to reason about than a gear train — but it "
                                    + "is itself a significant stress consumer.",
                            "Cogwheels must alternate large and small when meshed side by side. Two "
                                    + "of the same size placed adjacent will not connect."),
                            "cogwheel gear ratio speed up down large small rotation speed controller"))),

            new Topic("Belts and transport", "The rules that are easy to trip over", List.of(
                    new Entry("Belt placement rules", List.of(
                            "A belt is created by connecting two shafts with belt connectors. Both "
                                    + "shafts must be aligned on the same axis.",
                            "Maximum span is 20 blocks between the two ends.",
                            "Belts run flat, or at 45 degrees, or vertically — nothing in between.",
                            "Belts themselves cost no stress. Long belt runs are cheap; it is the "
                                    + "machines hanging off them that cost."),
                            "belt shaft connector length 20 diagonal vertical slope rules"),

                    new Entry("Getting items on and off", List.of(
                            "Funnels move items between an inventory and a belt, and care which way "
                                    + "they face — use a wrench to flip direction.",
                            "Chutes move items vertically and can pull from above or push below.",
                            "A belt will happily carry items past a full destination; add a "
                                    + "brass funnel with a filter if you need sorting.",
                            "Smart chutes and brass funnels accept filters; andesite ones do not."),
                            "funnel chute filter brass andesite sorting wrench direction"))),

            new Topic("Power sources", "What to build, and when", List.of(
                    new Entry("Progression of generators", List.of(
                            "Hand crank: temporary, for testing a single machine.",
                            "Water wheels: the early workhorse. They need flowing water along the "
                                    + "correct faces — check with Ponder, because a wheel touching "
                                    + "still water produces nothing.",
                            "Windmill bearing: needs a minimum number of sails and clear sky. Output "
                                    + "scales with sail count, so build wide rather than adding a "
                                    + "second small one.",
                            "Steam engines on a boiler: the endgame. Output depends on the boiler "
                                    + "level, which in turn depends on how much water and heat you "
                                    + "supply. A boiler starved of either quietly drops a level."),
                            "water wheel windmill sail steam engine boiler hand crank generator power"),

                    new Entry("Turning parts of a factory off", List.of(
                            "A clutch stops rotation past it when it receives a redstone signal.",
                            "A gearshift reverses rotation past it when it receives a signal.",
                            "A disengaged branch draws no stress at all, which makes clutches the "
                                    + "cheapest possible fix for an over-committed network.",
                            "Pair one with a redstone link or an observer on your output chest to "
                                    + "shut a production line down automatically when it is full."),
                            "clutch gearshift redstone disable branch automation idle"))),

            new Topic("Troubleshooting", "Work through these in order", List.of(
                    new Entry("Nothing is moving at all", List.of(
                            "1. Is there a generator attached, and is it actually generating? A "
                                    + "water wheel needs flowing water; a windmill needs to have been "
                                    + "started with a wrench on the bearing.",
                            "2. Is the network overstressed? Look for the flashing red bar.",
                            "3. Is the chain actually connected? Shafts and cogwheels only connect "
                                    + "along specific faces — Ponder shows the valid arrangements.",
                            "4. Did you place two cogwheels of the same size next to each other? "
                                    + "They will not mesh."),
                            "not moving stopped nothing works broken debug"),

                    new Entry("A machine runs but produces nothing", List.of(
                            "Presses and mixers need a basin underneath, placed one block below "
                                    + "with a clear gap.",
                            "A basin needs somewhere to output to, or it will fill and stall. Give "
                                    + "it a funnel or a belt on the correct side.",
                            "Crushing wheels must be a matched pair, turning towards each other. If "
                                    + "they turn the same way, items will not be pulled in.",
                            "Check the recipe actually exists in Create rather than in a vanilla "
                                    + "crafting grid — Ponder and JEI/EMI both show which machine a "
                                    + "recipe belongs to."),
                            "press mixer basin crushing wheels recipe output stuck nothing"))));

    private CreateKnowledge() {
    }

    public static List<Topic> topics() {
        return TOPICS;
    }

    /** All entries in a topic that match a search string. */
    public static List<Entry> search(Topic topic, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : topic.entries()) {
            if (entry.matches(needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Every matching entry across all topics, for a global search. */
    public static List<Entry> searchAll(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Topic topic : TOPICS) {
            for (Entry entry : topic.entries()) {
                if (entry.matches(needle)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    public static int totalEntries() {
        return TOPICS.stream().mapToInt(topic -> topic.entries().size()).sum();
    }
}
