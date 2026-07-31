package com.gadzo.client.integration.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reference material for the Create mod.
 *
 * <p>Written against Create 6.0.8 for 1.20.1. The numeric figures — stress values, speed tiers,
 * belt length, windmill sail ratios — were read out of the mod itself rather than recalled, and
 * where Create is installed the planner reads the live values instead, so a pack that retunes
 * them stays correct.
 *
 * <p>Content is weighted towards the things that trip people up rather than towards a complete
 * block list: stress arithmetic, what gearing can and cannot do, the rules that silently make a
 * contraption do nothing, and the order to work through when it has stopped. Recipes and item
 * lists are left to JEI and Ponder, which already do them better.
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
                                    + "hotbar; look at a generator and you see how much headroom is "
                                    + "left.",
                            "GADZO's Kinetic readout shows the same figures without the goggles, "
                                    + "reading the same fields Create syncs to your client. It is a "
                                    + "convenience, not extra information."),
                            "goggles engineer stress rpm readout hud overlay gadzo"),

                    new Entry("A wrench is not optional", List.of(
                            "The wrench rotates a placed block, flips a funnel's direction, and "
                                    + "picks blocks up without breaking them.",
                            "Most 'this funnel does nothing' problems are a funnel pointing the "
                                    + "wrong way. Right-clicking it with a wrench flips it.",
                            "Right-clicking a windmill bearing with a wrench is what starts the "
                                    + "windmill. Building the sails is not enough on its own."),
                            "wrench rotate flip funnel direction windmill bearing start pick up"))),

            new Topic("Stress", "Why everything stopped, and how the arithmetic works", List.of(
                    new Entry("How stress actually works", List.of(
                            "Every machine has an impact value and every generator a capacity "
                                    + "value. Both are per-RPM figures, not totals.",
                            "Create sums them per block, each at that block's own speed: stress "
                                    + "used = the sum of impact x RPM over every machine, and "
                                    + "capacity = the sum of capacity x RPM over every generator.",
                            "On a network that runs at one speed throughout, both sides scale "
                                    + "together, so gearing the whole thing up buys nothing at all. "
                                    + "It raises consumption and capacity by exactly the same factor.",
                            "This is the single most common misunderstanding in Create. If you are "
                                    + "overstressed, you need more generators or fewer machines. "
                                    + "Speeding the network up changes nothing."),
                            "stress su overstressed capacity impact rpm formula maths gearing"),

                    new Entry("The exception: gearing only the machines", List.of(
                            "Because each block contributes at its own speed, the rule above only "
                                    + "holds when the whole network runs at one speed.",
                            "If you gear up after the generator — a large-to-small pair between "
                                    + "the water wheel and the presses — the machines now draw at "
                                    + "the higher speed while the wheel still supplies at its own.",
                            "Doubling the machine side doubles the load with no extra capacity. "
                                    + "This is a real way to overstress a network that was fine a "
                                    + "moment ago, and it looks like the gearing 'cost' stress.",
                            "It did not. The machines simply do more work per second, and Create "
                                    + "charges for that honestly."),
                            "gear up machines only overstress after generator ratio speed side"),

                    new Entry("Fixing an overstressed network", List.of(
                            "Symptom: everything shudders to a halt and a red stress bar flashes.",
                            "1. Add generators. Water wheels are cheap and stack well; a windmill "
                                    + "needs open sky and sails; steam is the endgame answer.",
                            "2. Split the network. Two independent water wheels driving half the "
                                    + "factory each is often easier than one large shared line, and "
                                    + "it stops one jam taking everything down.",
                            "3. Turn machines off when idle. A clutch on a branch removes its whole "
                                    + "stress draw while disengaged — the cheapest possible fix.",
                            "4. Look for something you forgot. A stray drill or a deployer left "
                                    + "running in a corner is a classic cause."),
                            "overstressed red bar stopped shaking fix stress troubleshoot"),

                    new Entry("Impact values that matter (Create 6.0.8)", List.of(
                            "Per unit of RPM. Create calls anything at 4 a medium impact and "
                                    + "anything at 8 a high one.",
                            "Free: belts, shafts, chain drives and gearboxes cost nothing.",
                            "2 su/rpm: encased fan, mechanical arm, mechanical crafter, weighted "
                                    + "ejector.",
                            "4 su/rpm: millstone, mixer, saw, drill, deployer, mechanical pump, "
                                    + "rope pulley, mechanical bearing, hose pulley, turntable.",
                            "8 su/rpm: mechanical press, and each crushing wheel — so a working "
                                    + "pair costs 16.",
                            "Practical consequence: a bank of presses or crushing wheels dominates "
                                    + "any stress budget. Plan generation around those first and "
                                    + "treat everything else as rounding."),
                            "impact values su cost press crushing wheels mixer millstone list table"))),

            new Topic("Speed", "Insufficient speed, and what gearing can do", List.of(
                    new Entry("Speed is separate from stress", List.of(
                            "A machine can have plenty of stress available and still refuse to run "
                                    + "because it is turning too slowly.",
                            "Create's tiers, by default: below 30 RPM is slow, 30 to 99 is medium, "
                                    + "100 and above is fast. Machines state a minimum tier.",
                            "If a block reports insufficient speed, the fix is gearing, not more "
                                    + "power. Adding a second water wheel will not help.",
                            "The ceiling anywhere in a network is 256 RPM. Exceed it and shafts "
                                    + "snap rather than turning faster."),
                            "insufficient speed rpm slow minimum tier 30 100 256 limit"),

                    new Entry("Gearing is binary", List.of(
                            "A large cogwheel meshed with a small one changes speed by a factor of "
                                    + "two: large driving small doubles it, small driving large "
                                    + "halves it.",
                            "Everything else in the transmission chain — shafts, belts, gearboxes, "
                                    + "chain drives — passes speed through unchanged.",
                            "So the only ratios a cogwheel chain can produce are powers of two. "
                                    + "From 16 RPM you can reach 8, 32, 64 and so on, and nothing "
                                    + "in between. Three steps up is eight times the input.",
                            "For an exact speed that is not a power of two, the Rotational Speed "
                                    + "Controller sets any value from 1 to 256 directly. GADZO's "
                                    + "Gear ratios tab will tell you which case you are in.",
                            "Cogwheels must alternate large and small when meshed side by side. "
                                    + "Two of the same size placed adjacent will not connect."),
                            "cogwheel gear ratio speed up down large small rotation speed controller "
                                    + "power of two binary"),

                    new Entry("Reversing and stopping", List.of(
                            "A clutch stops rotation past it while it receives a redstone signal.",
                            "A gearshift reverses rotation past it while it receives a signal.",
                            "A sequenced gearshift runs a scripted list of movements — so many "
                                    + "degrees, then wait, then reverse — and is how most automatic "
                                    + "doors and piston sequences are built.",
                            "A disengaged branch draws no stress at all, which is why a clutch is "
                                    + "often a better answer to an overstressed network than another "
                                    + "generator."),
                            "clutch gearshift sequenced reverse stop redstone signal"))),

            new Topic("Power sources", "What to build, and when", List.of(
                    new Entry("The generators, with real numbers", List.of(
                            "Hand crank: 8 su/rpm at 32 RPM, so 256 SU while you hold it. For "
                                    + "testing one machine, nothing more.",
                            "Water wheel: 32 su/rpm at 8 RPM, so 256 SU each. Cheap, stackable, "
                                    + "and the early-game workhorse.",
                            "Large water wheel: 128 su/rpm at 4 RPM, so 512 SU. Twice the output "
                                    + "of a small one and far better per block of space used, but "
                                    + "half the speed — you will usually gear up after it.",
                            "Windmill bearing: 512 su/rpm, up to 16 RPM. Needs 8 sails minimum and "
                                    + "gains 1 RPM per 8 sails, so 128 sails reaches the cap.",
                            "Steam engine: 1024 su/rpm, up to 64 RPM. Output scales with the "
                                    + "boiler level, which depends on how much water and heat you "
                                    + "feed it. This is the endgame."),
                            "water wheel windmill sail steam engine boiler hand crank generator power "
                                    + "numbers su rpm"),

                    new Entry("Water wheels: the mistake everyone makes", List.of(
                            "A water wheel needs water that is flowing, and flowing across the "
                                    + "correct faces. A wheel sitting in still water produces "
                                    + "nothing at all and gives no warning.",
                            "Water flowing onto opposite sides in opposite directions cancels out. "
                                    + "Ponder the block and copy the arrangement exactly.",
                            "The reliable early build is a two-block-deep channel with a source "
                                    + "block at one end, so the flow runs one way past the wheel.",
                            "Stacking wheels side by side on one shaft works and is the simplest "
                                    + "way to scale early power."),
                            "water wheel flowing still not working nothing zero rpm channel source"),

                    new Entry("Boilers, briefly", List.of(
                            "A boiler is a multiblock of fluid tanks heated from below, with steam "
                                    + "engines attached to the sides.",
                            "Its level — and so its output — depends on three things at once: the "
                                    + "size of the tank structure, enough water supplied, and enough "
                                    + "heat under it. Starve any one and the level quietly drops.",
                            "A blaze burner on regular fuel gives a passive level; feeding it "
                                    + "blaze cakes gives the highest tier.",
                            "Check the level by looking at the boiler with goggles. If it says it "
                                    + "is short of water, the answer is more pumps, not more heat."),
                            "boiler steam engine blaze burner level water heat multiblock"))),

            new Topic("Belts and transport", "The rules that are easy to trip over", List.of(
                    new Entry("Belt placement rules", List.of(
                            "A belt is created by connecting two shafts with belt connectors. Both "
                                    + "shafts must be aligned on the same axis.",
                            "Maximum span is 20 blocks between the two ends.",
                            "Belts run flat, at 45 degrees, or vertically — nothing in between.",
                            "Belts cost no stress. Long runs are cheap; it is the machines hanging "
                                    + "off them that cost."),
                            "belt shaft connector length 20 diagonal vertical slope rules"),

                    new Entry("Getting items on and off", List.of(
                            "Funnels move items between an inventory and a belt, and care which way "
                                    + "they face. A wrench flips them.",
                            "Chutes move items vertically and can pull from above or push below.",
                            "A belt will happily carry items past a full destination. Add a brass "
                                    + "funnel with a filter if you need sorting.",
                            "Brass funnels and smart chutes accept filters; andesite ones do not.",
                            "A funnel placed on the side of a belt inserts; one placed facing along "
                                    + "it extracts. This is the source of most 'why is nothing "
                                    + "moving' confusion."),
                            "funnel chute filter brass andesite sorting wrench direction insert extract"),

                    new Entry("Sorting lines", List.of(
                            "The standard build is one belt with brass funnels along it, each "
                                    + "filtered to a different item, feeding into chests below.",
                            "Put the most common item last, not first — an unfiltered overflow at "
                                    + "the end of the line keeps the belt from backing up.",
                            "An attribute filter sorts by a property rather than an exact item: "
                                    + "all ores, all food, everything with a specific enchantment.",
                            "A smart chute does the same job vertically and is often tidier than a "
                                    + "belt for a small number of outputs."),
                            "sorting filter brass funnel attribute overflow chest line automation"))),

            new Topic("Processing", "Turning raw materials into things", List.of(
                    new Entry("Which machine does what", List.of(
                            "Millstone: crushes ores and mills materials, slowly, with no stress "
                                    + "requirement worth worrying about. This is the early-game "
                                    + "answer and it works from a hand crank.",
                            "Crushing wheels: the same job much faster, in a pair turning towards "
                                    + "each other. 8 su/rpm each, so 16 for the pair.",
                            "Mechanical press: makes plates and, over a basin, compacts.",
                            "Mechanical mixer: over a basin, mixes and — with a blaze burner "
                                    + "underneath — heats or superheats.",
                            "Mechanical saw: cuts logs into planks efficiently, and fells trees "
                                    + "when placed facing upward.",
                            "Deployer: performs a right-click with whatever item you give it, which "
                                    + "covers an enormous range of recipes."),
                            "millstone crushing wheels press mixer saw deployer machine which what"),

                    new Entry("Basins: the silent stall", List.of(
                            "Presses and mixers work on a basin placed one block below with a clear "
                                    + "gap between.",
                            "A basin needs somewhere to put its output or it fills and stops. Give "
                                    + "it a funnel or a belt on the correct side.",
                            "A basin outputs towards the side its bottom face is pointing — set "
                                    + "with a wrench. Getting this wrong is the most common reason a "
                                    + "mixing setup runs once and then stops.",
                            "Mixing recipes that need heat want a blaze burner directly under the "
                                    + "basin, not under the mixer."),
                            "basin press mixer output stuck stall full heat blaze burner"),

                    new Entry("Crushing wheels", List.of(
                            "They must be a matched pair, in line, turning towards each other. If "
                                    + "they turn the same way items are not pulled in.",
                            "The usual mistake is driving both from the same shaft, which makes "
                                    + "them turn the same way. Use a gearbox or an extra cogwheel "
                                    + "on one side to reverse it.",
                            "They will also damage entities, which is either a hazard or a mob farm "
                                    + "depending on your intent.",
                            "Feed from directly above and collect from directly below; anything "
                                    + "else works less reliably."),
                            "crushing wheels pair direction reverse gearbox damage mob farm"))),

            new Topic("Contraptions", "Moving structures", List.of(
                    new Entry("The four ways to move blocks", List.of(
                            "Mechanical piston: moves a structure in a straight line along piston "
                                    + "extension poles.",
                            "Rope pulley: moves a structure up and down on a rope.",
                            "Mechanical bearing: rotates a structure around itself.",
                            "Cart assembler with a minecart: moves a structure along rails, and is "
                                    + "the only one that travels any real distance.",
                            "All four assemble the blocks in front of them into one moving object. "
                                    + "Chassis and super glue decide what comes along."),
                            "piston pulley bearing cart assembler contraption move structure"),

                    new Entry("Why the contraption will not assemble", List.of(
                            "Something in the structure is not attached. Use linear chassis "
                                    + "(aligned with the direction of travel), radial chassis for "
                                    + "rotation, or super glue for arbitrary shapes.",
                            "The block limit was exceeded — large contraptions have a configurable "
                                    + "cap and will simply refuse.",
                            "An immovable block is in the way. Chests with contents move fine, but "
                                    + "some blocks are marked as never movable.",
                            "It is trying to move into an occupied space. Clear the path."),
                            "contraption assemble fail chassis super glue limit immovable stuck"),

                    new Entry("Harvesting and building contraptions", List.of(
                            "A mechanical harvester on a moving contraption crops a field in one "
                                    + "pass and replants it.",
                            "A mechanical plough clears grass and flowers and can till soil.",
                            "A mechanical drill or saw on a contraption mines or fells whatever it "
                                    + "passes through — the basis of every Create quarry.",
                            "Deployers on a contraption can place blocks, which is how automated "
                                    + "bridge and wall builders work.",
                            "Everything a contraption picks up goes into inventories attached to "
                                    + "the contraption itself. Put a chest on it or the items drop."),
                            "harvester plough drill saw quarry deployer farm automatic contraption"))),

            new Topic("Trains", "The long-distance answer", List.of(
                    new Entry("Building a train", List.of(
                            "Lay track with the Railway Casing and a track item; the track item "
                                    + "places curves and slopes automatically between two points.",
                            "A train needs a Train Station to be assembled at, and at least one "
                                    + "bogey. Two bogeys make a carriage that can carry a structure.",
                            "Assemble at the station, name the train, then drive it manually or "
                                    + "give it a schedule.",
                            "A schedule is a list of destinations and conditions — wait until "
                                    + "loaded, wait 30 seconds, wait for a redstone signal — and is "
                                    + "what turns a train into automated logistics."),
                            "train track bogey station schedule assemble carriage railway"),

                    new Entry("Signals, briefly", List.of(
                            "Train signals prevent collisions by dividing track into sections.",
                            "A regular signal holds a train until the section ahead is clear. A "
                                    + "chain signal holds it until the section after that is also "
                                    + "clear, which is what stops a train blocking a junction.",
                            "Rule of thumb: chain signals before a junction, regular signals after "
                                    + "it.",
                            "If trains keep stopping for no visible reason, you have a section with "
                                    + "no exit signal — the train is waiting on a block it can "
                                    + "never be told is clear."),
                            "signal chain junction collision block section train stuck waiting"))),

            new Topic("Survival start", "Create in a fresh world, in order", List.of(
                    new Entry("The first hour", List.of(
                            "Andesite alloy is the gate to everything. Andesite plus iron or zinc "
                                    + "nuggets — early on you can make it by hand in a crafting "
                                    + "grid, which is worth doing before automating it.",
                            "Build a millstone and a hand crank first. That alone doubles your ore "
                                    + "output and needs no infrastructure.",
                            "Then a water wheel, because holding a hand crank is not a power "
                                    + "system. One wheel is 256 SU, which runs a lot.",
                            "Then a mechanical press over a basin for plates, and a saw for planks. "
                                    + "At that point you are producing andesite alloy fast enough "
                                    + "for everything else to follow."),
                            "start beginning early game first survival progression andesite alloy "
                                    + "millstone water wheel"),

                    new Entry("What to automate, in order", List.of(
                            "1. Andesite alloy. You will need thousands and it is the input to "
                                    + "almost every other Create recipe.",
                            "2. Ore doubling. A crushing wheel pair fed from a chest pays for "
                                    + "itself in a single mining trip.",
                            "3. Wood. A saw pointed up fells trees; a deployer with bone meal and a "
                                    + "sapling replants. This unlocks casings and belts in bulk.",
                            "4. Food. A mechanical harvester on a bearing over a wheat field, or a "
                                    + "deployer line, ends the food problem permanently.",
                            "5. Only then think about trains, boilers and quarries. They are all "
                                    + "cheap once the first four are running and painful before."),
                            "automate order priority progression what first andesite ore wood food"),

                    new Entry("Create's ore doubling is your mining budget", List.of(
                            "Crushed ore from a millstone or crushing wheels smelts into more "
                                    + "ingots than the raw ore would.",
                            "Washing crushed ore in a fan-driven water stream gives nuggets of "
                                    + "other metals as a bonus, which is where most early zinc "
                                    + "comes from.",
                            "An encased fan pointed through a source of water becomes a washer; "
                                    + "through fire it becomes a smelter; through lava, a "
                                    + "smoker-equivalent. Same block, three jobs, 2 su/rpm each.",
                            "This is the highest-value thing Create adds to a survival world and it "
                                    + "is available within the first hour."),
                            "ore doubling washing fan bulk smelting nuggets zinc encased fan survival"))),

            new Topic("Troubleshooting", "Work through these in order", List.of(
                    new Entry("Nothing is moving at all", List.of(
                            "1. Is there a generator attached, and is it actually generating? A "
                                    + "water wheel needs flowing water; a windmill needs to have "
                                    + "been started with a wrench on the bearing.",
                            "2. Is the network overstressed? Look for the flashing red bar, or "
                                    + "GADZO's Kinetic readout.",
                            "3. Is the chain actually connected? Shafts and cogwheels only connect "
                                    + "along specific faces — Ponder shows the valid arrangements.",
                            "4. Did you place two cogwheels of the same size next to each other? "
                                    + "They will not mesh.",
                            "5. Is a clutch or gearshift receiving a signal it should not be?"),
                            "not moving stopped nothing works broken debug dead network"),

                    new Entry("A machine runs but produces nothing", List.of(
                            "Presses and mixers need a basin one block below with a clear gap.",
                            "A basin needs somewhere to output to, or it fills and stalls.",
                            "Crushing wheels must turn towards each other, not the same way.",
                            "Check the recipe actually belongs to that machine. JEI and EMI both "
                                    + "show which Create machine a recipe is for, and a recipe that "
                                    + "wants a mixer will never run in a press."),
                            "press mixer basin crushing wheels recipe output stuck nothing produced"),

                    new Entry("It worked yesterday and does not today", List.of(
                            "Something was added to the network. A single new deployer is 4 su/rpm "
                                    + "and can be what took you over the line.",
                            "A generator stopped: the water source got broken, the windmill lost "
                                    + "sails to a mob or a tree grew into it, the boiler ran dry.",
                            "Chunk loading. A contraption or generator in an unloaded chunk does "
                                    + "not run, and the part you can see stops with it.",
                            "A schedule or redstone line changed state and left a clutch engaged "
                                    + "when it should be disengaged."),
                            "was working now broken suddenly stopped chunk unloaded boiler dry"))));

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
