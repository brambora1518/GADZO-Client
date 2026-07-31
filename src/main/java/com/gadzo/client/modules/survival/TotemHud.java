package com.gadzo.client.modules.survival;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * How many Totems of Undying you are carrying.
 *
 * <p>Vanilla shows a totem's use as a full-screen animation after the fact — the moment it
 * matters is before that, when deciding whether a fight is safe to start. The number people
 * actually want at a glance is "do I have zero left", and that is worth a warning colour rather
 * than a count you have to read to interpret.
 *
 * <p>Counted across the main inventory and both hands, since a totem works from any of those
 * — it does not need to be in the offhand slot specifically, that is only the conventional
 * place to keep it reachable.
 */
public class TotemHud extends HudModule {

    private final BooleanSetting hideWhenNone;
    private final BooleanSetting hideWhenPlenty;

    public TotemHud() {
        super("Totems", "How many Totems of Undying you are carrying", ModuleCategory.SURVIVAL,
                HudAnchor.BOTTOM_RIGHT, -6, -140);
        this.hideWhenNone = addBool("Hide when zero", false,
                "Hide instead of warning when you are carrying none");
        this.hideWhenPlenty = addBool("Hide when plenty", true,
                "Hide once you are carrying more than a couple — nothing to watch there");
    }

    private int count() {
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                total += stack.getCount();
            }
        }
        if (player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            total += player.getOffHandStack().getCount();
        }
        return total;
    }

    private boolean visible() {
        int count = count();
        if (count == 0) {
            return !hideWhenNone.get();
        }
        return !(hideWhenPlenty.get() && count > 2);
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return visible() ? font.getWidth("Totems  99") : 0;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return visible() ? font.fontHeight : 0;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        if (!visible()) {
            return;
        }
        int count = count();
        double width = contentWidth(font);

        line(gfx, font, "Totems", 0, 0, Theme.textSecondary());
        Render2D.textRight(gfx, font, Integer.toString(count), width, 0,
                count == 0 ? Theme.danger() : count == 1 ? Theme.warning() : Theme.success(),
                hasTextShadow());
    }
}
