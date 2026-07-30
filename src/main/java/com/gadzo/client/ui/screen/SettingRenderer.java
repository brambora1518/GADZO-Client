package com.gadzo.client.ui.screen;

import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.ColorSetting;
import com.gadzo.client.core.setting.EnumSetting;
import com.gadzo.client.core.setting.KeybindSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.core.setting.Setting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws and handles input for one setting row in the mods menu.
 *
 * <p>Kept separate from the screen so the same widgets can be reused by any panel that needs
 * to expose settings. Hover and toggle animations live in a map keyed by the setting itself,
 * which means a row can be drawn from a stateless render pass and still animate.
 */
public final class SettingRenderer {

    public static final double ROW_HEIGHT = 24.0;
    private static final double TOGGLE_WIDTH = 28.0;
    private static final double TOGGLE_HEIGHT = 14.0;
    private static final double SLIDER_HEIGHT = 4.0;
    private static final double SWATCH_SIZE = 14.0;

    private static final Map<Setting<?>, Animation> HOVER = new HashMap<>();
    private static final Map<Setting<?>, Animation> TOGGLE = new HashMap<>();

    /** The slider currently being dragged, if any. */
    private static NumberSetting draggingSlider;

    /** The dropdown currently expanded, if any. */
    private static EnumSetting<?> openDropdown;

    private SettingRenderer() {
    }

    private static Animation hover(Setting<?> setting) {
        return HOVER.computeIfAbsent(setting, ignored -> new Animation(0.0, 150L));
    }

    private static Animation toggle(Setting<?> setting, boolean initial) {
        return TOGGLE.computeIfAbsent(setting, ignored -> new Animation(initial ? 1.0 : 0.0, 200L));
    }

    public static EnumSetting<?> openDropdown() {
        return openDropdown;
    }

    public static void closeDropdown() {
        openDropdown = null;
    }

    public static void releaseDrag() {
        draggingSlider = null;
    }

    /** Extra height a row needs beyond {@link #ROW_HEIGHT}, e.g. for an expanded dropdown. */
    public static double extraHeight(Setting<?> setting, Font font) {
        if (setting instanceof EnumSetting<?> enumSetting && openDropdown == enumSetting) {
            return enumSetting.constants().length * (font.lineHeight + 5) + 4;
        }
        return 0;
    }

    /**
     * Renders one setting row.
     *
     * @param width the full row width; controls are right-aligned within it
     */
    public static void render(GuiGraphicsExtractor gfx, Font font, Setting<?> setting,
                              double x, double y, double width, double mouseX, double mouseY) {
        boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + width, y + ROW_HEIGHT);
        Animation hoverAnimation = hover(setting);
        hoverAnimation.toBoolean(hovered);

        if (hoverAnimation.value() > 0.01) {
            Render2D.roundedRect(gfx, x - 4, y, width + 8, ROW_HEIGHT, Theme.radiusSmall(),
                    ColorUtil.fade(Theme.surfaceHover(), hoverAnimation.value() * 0.6));
        }

        double textY = y + (ROW_HEIGHT - font.lineHeight) / 2.0;
        Render2D.text(gfx, font, setting.getName(), x, textY, Theme.textSecondary());

        double right = x + width;
        if (setting instanceof BooleanSetting booleanSetting) {
            renderToggle(gfx, booleanSetting, right - TOGGLE_WIDTH, y + (ROW_HEIGHT - TOGGLE_HEIGHT) / 2.0);
        } else if (setting instanceof NumberSetting numberSetting) {
            renderSlider(gfx, font, numberSetting, x, y, width);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            renderDropdown(gfx, font, enumSetting, right, y, mouseX, mouseY);
        } else if (setting instanceof ColorSetting colorSetting) {
            renderSwatch(gfx, colorSetting, right - SWATCH_SIZE, y + (ROW_HEIGHT - SWATCH_SIZE) / 2.0);
        } else if (setting instanceof KeybindSetting keybindSetting) {
            renderKeybind(gfx, font, keybindSetting, right, y);
        }
    }

    private static void renderToggle(GuiGraphicsExtractor gfx, BooleanSetting setting, double x, double y) {
        Animation animation = toggle(setting, setting.get());
        animation.toBoolean(setting.get());
        double t = animation.value();

        int track = ColorUtil.mix(Theme.trackOff(), Theme.accent(), t);
        Render2D.roundedRect(gfx, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, TOGGLE_HEIGHT / 2.0, track);

        // Knob travels the track width minus its own diameter and the 2px inset either side.
        double knobRadius = TOGGLE_HEIGHT / 2.0 - 2;
        double travel = TOGGLE_WIDTH - TOGGLE_HEIGHT;
        double knobX = x + TOGGLE_HEIGHT / 2.0 + travel * t;
        Render2D.circle(gfx, knobX, y + TOGGLE_HEIGHT / 2.0, knobRadius, 0xFFFFFFFF);
    }

    private static void renderSlider(GuiGraphicsExtractor gfx, Font font, NumberSetting setting,
                                     double x, double y, double width) {
        String value = setting.display();
        Render2D.textRight(gfx, font, value, x + width, y + 3, Theme.textPrimary(), false);

        double trackY = y + ROW_HEIGHT - 7;
        Render2D.roundedRect(gfx, x, trackY, width, SLIDER_HEIGHT, SLIDER_HEIGHT / 2.0, Theme.trackOff());

        double filled = width * setting.asFraction();
        if (filled > 0.5) {
            Render2D.roundedRect(gfx, x, trackY, filled, SLIDER_HEIGHT, SLIDER_HEIGHT / 2.0, Theme.accent());
        }
        Render2D.circle(gfx, x + filled, trackY + SLIDER_HEIGHT / 2.0, 4.0, 0xFFFFFFFF);
    }

    private static void renderDropdown(GuiGraphicsExtractor gfx, Font font, EnumSetting<?> setting,
                                       double right, double y, double mouseX, double mouseY) {
        String label = setting.currentLabel();
        double boxWidth = Math.max(56, font.width(label) + 18);
        double boxX = right - boxWidth;
        double boxY = y + (ROW_HEIGHT - 16) / 2.0;

        Render2D.roundedRect(gfx, boxX, boxY, boxWidth, 16, Theme.radiusSmall(), Theme.surfaceHigh());
        Render2D.text(gfx, font, label, boxX + 6, boxY + 4, Theme.textPrimary());
        // Caret, flipped when the list is open.
        Render2D.textRight(gfx, font, openDropdown == setting ? "^" : "v",
                boxX + boxWidth - 5, boxY + 4, Theme.textMuted(), false);

        if (openDropdown != setting) {
            return;
        }

        double optionHeight = font.lineHeight + 5;
        double listY = boxY + 18;
        double listHeight = setting.constants().length * optionHeight + 4;
        Render2D.shadow(gfx, boxX, listY, boxWidth, listHeight, Theme.radiusSmall(), 4, Theme.shadowColor());
        Render2D.roundedRect(gfx, boxX, listY, boxWidth, listHeight, Theme.radiusSmall(), Theme.surfaceHigh());

        double optionY = listY + 2;
        for (Object constant : setting.constants()) {
            String optionLabel = labelOf(setting, constant);
            boolean optionHovered = MathUtil.within(mouseX, mouseY, boxX, optionY,
                    boxX + boxWidth, optionY + optionHeight);
            boolean selected = constant == setting.getValue();

            if (optionHovered || selected) {
                Render2D.roundedRect(gfx, boxX + 2, optionY, boxWidth - 4, optionHeight,
                        Theme.radiusSmall(),
                        selected ? ColorUtil.withAlpha(Theme.accent(), 70) : Theme.surfaceHover());
            }
            Render2D.text(gfx, font, optionLabel, boxX + 6, optionY + 2,
                    selected ? Theme.accent() : Theme.textSecondary());
            optionY += optionHeight;
        }
    }

    /** Bridges the wildcard so the enum's own labeller can be used without an unchecked cast. */
    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> String labelOf(EnumSetting<E> setting, Object constant) {
        return setting.label((E) constant);
    }

    private static void renderSwatch(GuiGraphicsExtractor gfx, ColorSetting setting, double x, double y) {
        Render2D.roundedRect(gfx, x - 1, y - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2,
                Theme.radiusSmall(), Theme.border());
        Render2D.roundedRect(gfx, x, y, SWATCH_SIZE, SWATCH_SIZE, Theme.radiusSmall(), setting.get());
    }

    private static void renderKeybind(GuiGraphicsExtractor gfx, Font font, KeybindSetting setting,
                                      double right, double y) {
        String label = setting.display();
        double boxWidth = Math.max(46, font.width(label) + 14);
        double boxX = right - boxWidth;
        double boxY = y + (ROW_HEIGHT - 16) / 2.0;

        int background = setting.isListening()
                ? ColorUtil.withAlpha(Theme.accent(), 90)
                : Theme.surfaceHigh();
        Render2D.roundedRect(gfx, boxX, boxY, boxWidth, 16, Theme.radiusSmall(), background);
        Render2D.textCentered(gfx, font, label, boxX + boxWidth / 2.0, boxY + 4,
                setting.isListening() ? Theme.accent() : Theme.textPrimary(), false);
    }

    // -- input ---------------------------------------------------------------------------

    /**
     * Handles a click on a setting row.
     *
     * @return whether the click was consumed
     */
    public static boolean mouseClicked(Setting<?> setting, Font font, double x, double y, double width,
                                       double mouseX, double mouseY, int button) {
        double right = x + width;

        // An open dropdown swallows clicks anywhere in its list before anything else sees them.
        if (setting instanceof EnumSetting<?> enumSetting && openDropdown == enumSetting) {
            if (selectFromDropdown(enumSetting, font, right, y, mouseX, mouseY)) {
                return true;
            }
        }

        boolean onRow = MathUtil.within(mouseX, mouseY, x, y, right, y + ROW_HEIGHT);
        if (!onRow) {
            return false;
        }

        if (setting instanceof BooleanSetting booleanSetting) {
            booleanSetting.toggle();
            return true;
        }
        if (setting instanceof NumberSetting numberSetting) {
            draggingSlider = numberSetting;
            numberSetting.setFromFraction((mouseX - x) / width);
            return true;
        }
        if (setting instanceof EnumSetting<?> enumSetting) {
            openDropdown = openDropdown == enumSetting ? null : enumSetting;
            return true;
        }
        if (setting instanceof KeybindSetting keybindSetting) {
            keybindSetting.setListening(!keybindSetting.isListening());
            return true;
        }
        if (setting instanceof ColorSetting colorSetting) {
            // Right-click cycles alpha presets; left-click steps the hue. A full picker would
            // need its own popup surface, which the settings column has no room for.
            if (button == 1 && colorSetting.allowsAlpha()) {
                int alpha = ColorUtil.alpha(colorSetting.get());
                colorSetting.setValue(ColorUtil.withAlpha(colorSetting.get(), alpha >= 255 ? 128 : 255));
            } else {
                colorSetting.setValue(ColorUtil.hsb(
                        (float) ((System.currentTimeMillis() % 3600) / 3600.0), 0.7f, 1.0f,
                        ColorUtil.alpha(colorSetting.get())));
            }
            return true;
        }
        return false;
    }

    private static boolean selectFromDropdown(EnumSetting<?> setting, Font font, double right, double y,
                                              double mouseX, double mouseY) {
        double label = font.width(setting.currentLabel());
        double boxWidth = Math.max(56, label + 18);
        double boxX = right - boxWidth;
        double optionHeight = font.lineHeight + 5;
        double optionY = y + (ROW_HEIGHT - 16) / 2.0 + 18 + 2;

        for (Object constant : setting.constants()) {
            if (MathUtil.within(mouseX, mouseY, boxX, optionY, boxX + boxWidth, optionY + optionHeight)) {
                assign(setting, constant);
                openDropdown = null;
                return true;
            }
            optionY += optionHeight;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> void assign(EnumSetting<E> setting, Object constant) {
        setting.setValue((E) constant);
    }

    /** Continues a slider drag; call from the screen's drag handler. */
    public static void mouseDragged(double rowX, double width, double mouseX) {
        if (draggingSlider != null) {
            draggingSlider.setFromFraction((mouseX - rowX) / width);
        }
    }

    public static boolean isDragging() {
        return draggingSlider != null;
    }

    /** Routes a key press to a listening keybind row. */
    public static boolean keyPressed(Setting<?> setting, int key) {
        return setting instanceof KeybindSetting keybind && keybind.acceptPress(key);
    }
}
