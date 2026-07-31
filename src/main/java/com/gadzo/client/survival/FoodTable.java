package com.gadzo.client.survival;

import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every edible item in the game, ranked by how long it keeps you fed.
 *
 * <p>Built from the item registry at runtime rather than typed out, which means it covers
 * modded food as well — Create's bread and its sweet rolls appear next to the vanilla entries
 * without this client knowing they exist.
 *
 * <p>The ranking is by saturation rather than by hunger restored, because saturation is what
 * decides how long before the hunger bar starts falling again and whether you regenerate. A
 * steak and a bowl of mushroom stew both fill six hunger points; the steak lasts more than twice
 * as long. That difference is invisible in game and is the reason this list exists.
 */
public final class FoodTable {

    /**
     * One food, with the two numbers that matter.
     *
     * @param name       display name
     * @param hunger     hunger points restored, out of 20
     * @param saturation saturation restored
     */
    public record Food(String name, int hunger, double saturation) {

        /**
         * Saturation per hunger point.
         *
         * <p>The efficiency figure: how much staying power each point of bar space buys. High
         * values are what you carry on a long trip, low values are what you eat at home.
         */
        public double ratio() {
            return hunger == 0 ? 0 : saturation / hunger;
        }
    }

    /** Built once — the registry does not change after load. */
    private static List<Food> cached;

    private FoodTable() {
    }

    public static synchronized List<Food> all() {
        if (cached != null) {
            return cached;
        }
        List<Food> foods = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            FoodComponent component = item.getFoodComponent();
            if (component == null) {
                continue;
            }
            int hunger = component.getHunger();
            // Minecraft stores saturation as a multiplier, not a value: the actual saturation
            // restored is twice the hunger times that modifier. Showing the raw modifier would
            // put golden carrots and steak on the same footing, which they are not.
            double saturation = hunger * component.getSaturationModifier() * 2.0;
            foods.add(new Food(item.getName().getString(), hunger, saturation));
        }
        foods.sort(Comparator.comparingDouble(Food::saturation).reversed()
                .thenComparing(Food::name));
        cached = List.copyOf(foods);
        return cached;
    }

    /** The best few, for a compact summary. */
    public static List<Food> best(int limit) {
        List<Food> foods = all();
        return foods.size() <= limit ? foods : foods.subList(0, limit);
    }
}
