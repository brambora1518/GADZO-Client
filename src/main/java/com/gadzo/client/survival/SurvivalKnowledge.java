package com.gadzo.client.survival;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reference material for vanilla survival, written against 1.20.1.
 *
 * <p>Chosen for the same reason as the Create reference: these are the things whose rules
 * changed at some point and whose old versions are still repeated everywhere. Mob spawning has
 * needed light level zero since 1.18 and half the internet still says seven. Ore heights were
 * rewritten in the same update. Anvil costs have never been explained in game at all.
 *
 * <p>Anything that can be read from the game instead of written down is — the food table is
 * built from the item registry, not from this file.
 */
public final class SurvivalKnowledge {

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

            new Topic("Mob spawning", "Lighting up, and why torches every 7 blocks is wrong",
                    List.of(
                    new Entry("The rule changed in 1.18", List.of(
                            "Hostile mobs need a block light level of exactly 0 to spawn. Not 7, "
                                    + "not below 8 — zero.",
                            "This means a single torch spawn-proofs a surprisingly large area, "
                                    + "and that the old advice of a torch every seven blocks is "
                                    + "wildly over-cautious.",
                            "Sky light does not prevent spawns on its own. An unlit room under a "
                                    + "glass roof is dark at night and mobs will spawn in it.",
                            "GADZO's Light level element shows the block light where you stand and "
                                    + "says outright whether a spawn is possible there."),
                            "light level spawn mob torch dark 0 zero spawnproof lighting"),

                    new Entry("Where mobs come from", List.of(
                            "Hostile mobs spawn between 24 and 128 blocks from you. Closer than "
                                    + "24 and nothing spawns; further than 128 and it despawns.",
                            "That 24-block gap is why an AFK spot works: stand far enough from a "
                                    + "farm and the game has nowhere else nearby to put mobs.",
                            "They need an opaque, full block to stand on with space above. "
                                    + "Slabs on the upper half, and most non-full blocks, do not "
                                    + "qualify.",
                            "There is a global cap on hostile mobs loaded at once. A cave system "
                                    + "left dark near your base is competing with your farm for it, "
                                    + "which is why lighting caves helps farm rates."),
                            "spawn radius 24 128 despawn mob cap afk farm rates"))),

            new Topic("Mining", "Where the ores actually are in 1.20", List.of(
                    new Entry("Ore heights", List.of(
                            "Diamond: increases all the way down, best around y = -59 to -53. "
                                    + "Below -59 the extra deepslate exposure stops helping.",
                            "Redstone: same shape as diamond, richest below y = -59.",
                            "Gold: peaks around y = -16. Also common in badlands at normal heights.",
                            "Iron: two peaks, one around y = 15 and a large one high in mountains "
                                    + "around y = 232.",
                            "Copper: peaks around y = 48, and far more common in dripstone caves.",
                            "Lapis: peaks around y = 0.",
                            "Emerald: mountains only, richer the higher you go, peaking near the "
                                    + "top of the world.",
                            "Coal: common from y = 0 upwards, peaking around y = 96."),
                            "ore height y level diamond iron gold copper redstone lapis emerald coal"),

                    new Entry("Branch mining is not the fastest way", List.of(
                            "Since 1.18, large open caves and deepslate cave systems expose far "
                                    + "more ore per block travelled than tunnelling does.",
                            "The efficient method is to find a deep cave, light it, and walk it.",
                            "If you do tunnel, y = -58 with two-block-high tunnels three apart is "
                                    + "close to optimal for diamond.",
                            "Fortune III roughly doubles diamond and redstone yield and is worth "
                                    + "more than any amount of extra tunnelling.",
                            "Take a water bucket. Lava at these depths is the main cause of death "
                                    + "and losing the whole trip."),
                            "branch mining strip tunnel efficient cave diamond fortune lava"))),

            new Topic("Enchanting", "Tables, bookshelves and the anvil's hidden costs", List.of(
                    new Entry("Getting level 30 offers", List.of(
                            "An enchanting table needs 15 bookshelves to reach its top tier.",
                            "They must be one block away from the table with air between, at the "
                                    + "same level or one above. A bookshelf pushed flush against "
                                    + "the table does nothing.",
                            "Level 30 offers cost 3 levels to apply, not 30. The level requirement "
                                    + "is a gate, not a price.",
                            "Enchanting a book rather than the tool lets you keep the result and "
                                    + "choose what it goes on, at the cost of a worse average roll."),
                            "enchanting table bookshelf 15 level 30 lapis book"),

                    new Entry("Why the anvil says Too Expensive", List.of(
                            "Every anvil operation on an item raises its prior work penalty, and "
                                    + "the penalty doubles each time: 1, 3, 7, 15, 31 levels.",
                            "Once the total cost of an operation would exceed 39 levels, the anvil "
                                    + "refuses outright and says Too Expensive. In survival there "
                                    + "is no way back.",
                            "The fix is order. Combine books with books first, building one book "
                                    + "with everything on it, then apply that to a fresh tool. Two "
                                    + "operations on the tool instead of six.",
                            "Renaming costs a level and also adds to the penalty, so name things "
                                    + "last, not first.",
                            "A grindstone strips enchantments and resets the penalty, at the cost "
                                    + "of everything on the item."),
                            "anvil too expensive prior work penalty combine order grindstone rename"))),

            new Topic("Brewing", "The tree, from nether wart outwards", List.of(
                    new Entry("Every potion starts the same way", List.of(
                            "Water bottle plus nether wart makes an Awkward Potion. Almost "
                                    + "everything else is one ingredient on top of that.",
                            "Speed: sugar.  Strength: blaze powder.  Healing: glistering melon.",
                            "Regeneration: ghast tear.  Fire Resistance: magma cream.",
                            "Water Breathing: pufferfish.  Night Vision: golden carrot.",
                            "Leaping: rabbit's foot.  Slow Falling: phantom membrane.",
                            "Turtle Master: turtle shell.  Poison: spider eye — and that one does "
                                    + "not need the awkward base."),
                            "brewing potion recipe nether wart awkward ingredient list"),

                    new Entry("Modifiers", List.of(
                            "Redstone extends duration. Glowstone raises potency, and shortens "
                                    + "the duration in exchange — you cannot have both.",
                            "Gunpowder turns a potion into a splash potion, at the cost of some "
                                    + "duration.",
                            "Dragon's breath turns a splash potion into a lingering one.",
                            "A fermented spider eye corrupts a potion into its opposite: healing "
                                    + "becomes harming, night vision becomes invisibility, "
                                    + "swiftness becomes slowness.",
                            "Brew in batches of three. The stand takes three bottles for the same "
                                    + "single ingredient, so brewing one at a time wastes two "
                                    + "thirds of everything."),
                            "redstone glowstone gunpowder splash lingering fermented spider eye "
                                    + "corrupt batch three"))),

            new Topic("The Nether", "Portals, linking and getting back", List.of(
                    new Entry("Eight to one, but not on Y", List.of(
                            "One block in the Nether is eight in the Overworld on X and Z. The Y "
                                    + "axis is not scaled at all.",
                            "Dividing your Y by eight is a common mistake and puts the portal in "
                                    + "the lava sea or the roof. Build at whatever height is "
                                    + "convenient.",
                            "The Nether calculator tab does the arithmetic and can convert the "
                                    + "position you are standing in."),
                            "nether portal ratio 8 1 coordinates convert y height"),

                    new Entry("Why both my portals go to the same place", List.of(
                            "When you step through, the game looks for an existing portal within "
                                    + "128 blocks on the Overworld side, or 16 on the Nether side, "
                                    + "and reuses it rather than building a new one.",
                            "Two Overworld portals less than 1024 blocks apart map to Nether "
                                    + "positions within 128 of each other, so they can share a "
                                    + "destination.",
                            "The reliable fix is to build both ends by hand: work out the "
                                    + "coordinates, build the far portal at exactly that spot, and "
                                    + "light it yourself before travelling.",
                            "Once a pair is linked correctly it stays linked, as long as neither "
                                    + "portal is broken."),
                            "portal link same place two destination 128 16 build manually"))),

            new Topic("Staying alive", "The things that actually kill people", List.of(
                    new Entry("Food is a health system", List.of(
                            "You only regenerate health at 18 hunger points or above. Below that "
                                    + "you heal not at all until you eat.",
                            "Saturation is a hidden value behind the hunger bar. It drains first, "
                                    + "and only when it is empty does hunger start dropping.",
                            "So the food to carry is not the one that fills the most bar, it is "
                                    + "the one with the most saturation — golden carrots and "
                                    + "steak, not bread.",
                            "The Food table tab ranks every edible item in your game, including "
                                    + "modded ones, by exactly that."),
                            "food hunger saturation regen heal 18 golden carrot steak bread"),

                    new Entry("Before you go anywhere", List.of(
                            "A water bucket stops fall damage, puts out fire, and gets you out of "
                                    + "lava. It is the single most valuable slot in your inventory.",
                            "Fire resistance makes the Nether trivial and lava survivable. Brew "
                                    + "it before the first trip, not after the first death.",
                            "A shield blocks skeleton arrows, creeper explosions and most melee. "
                                    + "It is one iron and six planks.",
                            "Turn your death point on. GADZO records it automatically and the beam "
                                    + "is visible from a long way off; the vanilla chat message is "
                                    + "gone in seconds."),
                            "water bucket fire resistance shield preparation death gear checklist"),

                    new Entry("Fall damage and the void", List.of(
                            "Fall damage starts above three blocks and is one half-heart per block "
                                    + "after that.",
                            "Feather Falling IV reduces it by 48%, which turns most survivable "
                                    + "falls into scratches.",
                            "Water, a hay bale, or slime blocks cancel it entirely. Powder snow "
                                    + "does too and is easy to carry in a bucket.",
                            "In the End, the void is the real hazard, and nothing protects against "
                                    + "it. Bridge with a block in hand and sneak."),
                            "fall damage feather falling water hay bale slime powder snow void end"))));

    private SurvivalKnowledge() {
    }

    public static List<Topic> topics() {
        return TOPICS;
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
