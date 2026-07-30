package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.EnumSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Equipped armour and held item with durability.
 *
 * <p>Durability is shown as a percentage and as a thin bar tinted from green to red, which is
 * far easier to read mid-fight than vanilla's item damage bar.
 */
public class ArmorHud extends HudModule {

    /** Layout direction for the item row. */
    public enum Layout {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final double ICON = 16.0;
    private static final double SPACING = 4.0;
    private static final double BAR_HEIGHT = 2.0;

    private final EnumSetting<Layout> layout;
    private final BooleanSetting showHeldItem;
    private final BooleanSetting showDurability;
    private final BooleanSetting hideEmpty;

    public ArmorHud() {
        super("Armour", "Equipped armour and durability", HudAnchor.BOTTOM_CENTER, 0, -60);
        this.layout = addEnum("Layout", Layout.HORIZONTAL, "Row or column");
        this.showHeldItem = addBool("Held item", true, "Include the item in your main hand");
        this.showDurability = addBool("Durability", true, "Show a percentage and wear bar");
        this.hideEmpty = addBool("Hide empty", true, "Skip slots with nothing equipped");
    }

    /** The stacks to draw, in display order, honouring the "hide empty" setting. */
    private List<ItemStack> stacks() {
        List<ItemStack> result = new ArrayList<>(5);
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return result;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getEquippedStack(slot);
            if (!stack.isEmpty() || !hideEmpty.get()) {
                result.add(stack);
            }
        }
        if (showHeldItem.get()) {
            ItemStack held = player.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!held.isEmpty() || !hideEmpty.get()) {
                result.add(held);
            }
        }
        return result;
    }

    /** Remaining durability in {@code [0, 1]}, or -1 when the item has none. */
    private static double durability(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
            return -1;
        }
        return (stack.getMaxDamage() - stack.getDamage()) / (double) stack.getMaxDamage();
    }

    private static int durabilityColor(double fraction) {
        if (fraction > 0.5) return Theme.success();
        if (fraction > 0.25) return Theme.warning();
        return Theme.danger();
    }

    /** Height of one cell, including the durability text and bar when enabled. */
    private double cellHeight(TextRenderer font) {
        return showDurability.get() ? ICON + 2 + font.fontHeight + BAR_HEIGHT + 1 : ICON;
    }

    private double cellWidth(TextRenderer font) {
        // Wide enough for "100%" so the row does not jitter as durability ticks down.
        return showDurability.get() ? Math.max(ICON, font.getWidth("100%")) : ICON;
    }

    @Override
    public double contentWidth(TextRenderer font) {
        int count = Math.max(1, stacks().size());
        return layout.is(Layout.HORIZONTAL)
                ? count * cellWidth(font) + (count - 1) * SPACING
                : cellWidth(font);
    }

    @Override
    public double contentHeight(TextRenderer font) {
        int count = Math.max(1, stacks().size());
        return layout.is(Layout.HORIZONTAL)
                ? cellHeight(font)
                : count * cellHeight(font) + (count - 1) * SPACING;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        List<ItemStack> stacks = stacks();
        double cellW = cellWidth(font);
        double cellH = cellHeight(font);

        double x = 0;
        double y = 0;
        for (ItemStack stack : stacks) {
            // Centre the 16px icon inside a cell that may be wider for the percentage text.
            double iconX = x + (cellW - ICON) / 2.0;
            if (!stack.isEmpty()) {
                gfx.drawItem(stack, (int) Math.round(iconX), (int) Math.round(y));
                gfx.drawItemInSlot(font, stack, (int) Math.round(iconX), (int) Math.round(y));
            }

            double fraction = durability(stack);
            if (showDurability.get() && fraction >= 0) {
                int color = durabilityColor(fraction);
                String label = Math.round(fraction * 100) + "%";
                Render2D.textCentered(gfx, font, label, x + cellW / 2.0, y + ICON + 2, color,
                        hasTextShadow());

                double barY = y + ICON + 2 + font.fontHeight;
                Render2D.rect(gfx, x, barY, cellW, BAR_HEIGHT,
                        ColorUtil.withAlpha(Theme.trackOff(), 170));
                Render2D.rect(gfx, x, barY, cellW * fraction, BAR_HEIGHT, color);
            }

            if (layout.is(Layout.HORIZONTAL)) {
                x += cellW + SPACING;
            } else {
                y += cellH + SPACING;
            }
        }
    }
}
