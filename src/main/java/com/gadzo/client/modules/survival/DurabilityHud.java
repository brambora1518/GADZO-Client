package com.gadzo.client.modules.survival;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Everything you are wearing or holding that is close to breaking.
 *
 * <p>Vanilla shows a durability bar under an item only once it is damaged at all, in a colour
 * that is hard to read at a glance, and only for the stack in your hand. The failure this
 * prevents is specific: a pickaxe that snaps mid-cave, or an elytra that runs out over the void.
 *
 * <p>Sorted by remaining fraction rather than by slot, because the question is always "what
 * breaks first", and unbreakable or undamageable items are left out entirely rather than shown
 * at 100% — a list that is mostly noise gets ignored.
 */
public class DurabilityHud extends HudModule {

    private static final double WIDTH = 112.0;
    private static final double BAR_HEIGHT = 3.0;

    private final NumberSetting warnBelow;
    private final BooleanSetting onlyWarnings;
    private final BooleanSetting showBars;
    private final BooleanSetting showPercent;

    public DurabilityHud() {
        super("Durability", "Gear that is close to breaking", ModuleCategory.SURVIVAL,
                HudAnchor.BOTTOM_LEFT, 6, -110);
        this.warnBelow = addNumber("Warn below", 20, 5, 100, 5,
                "Highlight gear under this much durability")
                .suffix("%");
        this.onlyWarnings = addBool("Only warnings", false,
                "Hide gear that is above the warning threshold");
        this.showBars = addBool("Bars", true, "Draw a durability bar under each item");
        this.showPercent = addBool("Percent", true, "Show the remaining percentage");
    }

    /** One item worth listing, with its remaining fraction precomputed. */
    private record Entry(String name, double remaining) {
    }

    /**
     * Collects damageable gear from armour, main hand and off hand.
     *
     * <p>Deliberately not the whole inventory: a chest full of half-used tools is not something
     * anyone needs warning about, and it would push the list off the screen.
     */
    private List<Entry> entries() {
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(player.getMainHandStack());
        stacks.add(player.getOffHandStack());
        stacks.addAll(player.getInventory().armor);

        double threshold = warnBelow.get() / 100.0;
        List<Entry> entries = new ArrayList<>();

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || !stack.isDamageable()) {
                continue;
            }
            int max = stack.getMaxDamage();
            if (max <= 0) {
                continue;
            }
            double remaining = MathUtil.clamp((max - stack.getDamage()) / (double) max, 0.0, 1.0);
            if (onlyWarnings.get() && remaining > threshold) {
                continue;
            }
            entries.add(new Entry(stack.getName().getString(), remaining));
        }
        entries.sort(Comparator.comparingDouble(Entry::remaining));
        return entries;
    }

    private double rowHeight(TextRenderer font) {
        return font.fontHeight + (showBars.get() ? BAR_HEIGHT + 2 : 0) + 3;
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return entries().isEmpty() ? 0 : WIDTH;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        int count = entries().size();
        return count == 0 ? 0 : count * rowHeight(font) - 3;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        double y = 0;
        double threshold = warnBelow.get() / 100.0;

        for (Entry entry : entries()) {
            int color = colorFor(entry.remaining(), threshold);
            int nameWidth = (int) (WIDTH - (showPercent.get() ? 32 : 0));

            line(gfx, font, Render2D.truncate(font, entry.name(), nameWidth), 0, y,
                    Theme.textPrimary());
            if (showPercent.get()) {
                Render2D.textRight(gfx, font,
                        Math.round(entry.remaining() * 100) + "%", WIDTH, y, color,
                        hasTextShadow());
            }
            y += font.fontHeight + 2;

            if (showBars.get()) {
                Render2D.roundedRect(gfx, 0, y, WIDTH, BAR_HEIGHT, BAR_HEIGHT / 2.0,
                        ColorUtil.withAlpha(Theme.trackOff(), 190));
                double filled = WIDTH * entry.remaining();
                if (filled > 0.5) {
                    Render2D.roundedRect(gfx, 0, y, filled, BAR_HEIGHT, BAR_HEIGHT / 2.0, color);
                }
                y += BAR_HEIGHT + 2;
            }
            y += 1;
        }
    }

    /**
     * Colour for a remaining fraction.
     *
     * <p>Red is reserved for the last tenth. The warning threshold is amber, so a player who
     * has set it at 50% still sees the difference between "worth repairing soon" and "about to
     * snap on the next block".
     */
    private int colorFor(double remaining, double threshold) {
        if (remaining <= 0.1) return Theme.danger();
        if (remaining <= threshold) return Theme.warning();
        return Theme.success();
    }
}
