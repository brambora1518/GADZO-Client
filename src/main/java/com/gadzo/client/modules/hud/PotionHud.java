package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Active potion effects with remaining time.
 *
 * <p>Effects are sorted by remaining duration so the one about to expire is always at a
 * predictable end of the list, and tinted by whether they help or hurt.
 */
public class PotionHud extends HudModule {

    /** Ticks per second, for converting durations into a clock. */
    private static final int TPS = 20;

    /** Vanilla marks effects longer than this as infinite. */
    private static final int INFINITE_DURATION = 32767 * TPS;

    private final BooleanSetting showAmplifier;
    private final BooleanSetting colorByType;
    private final BooleanSetting hideAmbient;

    public PotionHud() {
        super("Potions", "Active effects and remaining time", HudAnchor.TOP_RIGHT, -4, 20);
        this.showAmplifier = addBool("Level", true, "Show the effect level as a roman numeral");
        this.colorByType = addBool("Colour by type", true, "Green for buffs, red for debuffs");
        this.hideAmbient = addBool("Hide beacon effects", false, "Skip ambient (beacon) effects");
    }

    private List<StatusEffectInstance> effects() {
        List<StatusEffectInstance> result = new ArrayList<>();
        ClientPlayerEntity player = Mc.player();
        if (player == null) {
            return result;
        }
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            if (hideAmbient.get() && effect.isAmbient()) {
                continue;
            }
            result.add(effect);
        }
        result.sort(Comparator.comparingInt(StatusEffectInstance::getDuration));
        return result;
    }

    private String label(StatusEffectInstance effect) {
        StringBuilder text = new StringBuilder(effect.getEffectType().getName().getString());
        if (showAmplifier.get() && effect.getAmplifier() > 0) {
            text.append(' ').append(roman(effect.getAmplifier() + 1));
        }
        text.append("  ").append(duration(effect));
        return text.toString();
    }

    private static String duration(StatusEffectInstance effect) {
        if (effect.isInfinite() || effect.getDuration() >= INFINITE_DURATION) {
            return "**:**";
        }
        int seconds = effect.getDuration() / TPS;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Roman numerals for effect levels; falls back to digits past the sensible range. */
    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(value);
        };
    }

    private int color(StatusEffectInstance effect) {
        if (!colorByType.get()) {
            return Theme.textPrimary();
        }
        StatusEffectCategory category = effect.getEffectType().getCategory();
        return switch (category) {
            case BENEFICIAL -> Theme.success();
            case HARMFUL -> Theme.danger();
            default -> Theme.textSecondary();
        };
    }

    @Override
    public double contentWidth(TextRenderer font) {
        double widest = 0;
        for (StatusEffectInstance effect : effects()) {
            widest = Math.max(widest, font.getWidth(label(effect)));
        }
        return widest;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return Math.max(0, effects().size()) * font.fontHeight;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        double y = 0;
        for (StatusEffectInstance effect : effects()) {
            line(gfx, font, label(effect), 0, y, color(effect));
            y += font.fontHeight;
        }
    }
}
