package com.gadzo.client.ui.widget;

import com.gadzo.client.core.setting.ColorSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A saturation/value field with hue and alpha sliders.
 *
 * <p>Opened inline beneath a colour swatch in the mods menu. HSV is kept as the editing
 * state rather than being re-derived from the packed colour every frame: round-tripping
 * through RGB loses hue whenever saturation or value reaches zero, which would make the hue
 * slider jump around while dragging in the black or white corners.
 */
public class ColorPicker {

    public static final double WIDTH = 130.0;
    private static final double FIELD_HEIGHT = 78.0;
    private static final double SLIDER_HEIGHT = 9.0;
    private static final double GAP = 5.0;

    /** Columns drawn across the saturation axis; 2 px reads as smooth at GUI scale. */
    private static final int FIELD_STEP = 2;

    /** Which control a drag started on, so it keeps receiving movement. */
    private enum Grab { NONE, FIELD, HUE, ALPHA }

    private final ColorSetting setting;

    private float hue;
    private float saturation;
    private float value;
    private int alpha;

    private Grab grab = Grab.NONE;

    public ColorPicker(ColorSetting setting) {
        this.setting = setting;
        readFrom(setting.get());
    }

    public ColorSetting setting() {
        return setting;
    }

    /** Total height, including the alpha slider when the setting allows one. */
    public double height() {
        double height = FIELD_HEIGHT + GAP + SLIDER_HEIGHT;
        if (setting.allowsAlpha()) {
            height += GAP + SLIDER_HEIGHT;
        }
        return height + GAP + 10;
    }

    private void readFrom(int color) {
        this.alpha = ColorUtil.alpha(color);
        float r = ColorUtil.red(color) / 255.0f;
        float g = ColorUtil.green(color) / 255.0f;
        float b = ColorUtil.blue(color) / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        this.value = max;
        this.saturation = max == 0.0f ? 0.0f : delta / max;

        if (delta == 0.0f) {
            this.hue = 0.0f;
        } else if (max == r) {
            this.hue = ((g - b) / delta / 6.0f + 1.0f) % 1.0f;
        } else if (max == g) {
            this.hue = ((b - r) / delta + 2.0f) / 6.0f;
        } else {
            this.hue = ((r - g) / delta + 4.0f) / 6.0f;
        }
    }

    private void writeBack() {
        setting.setValue(ColorUtil.hsb(hue, saturation, value, alpha));
    }

    // -- rendering ---------------------------------------------------------------------

    public void render(DrawContext gfx, TextRenderer font, double x, double y) {
        Render2D.shadow(gfx, x, y, WIDTH, height(), Theme.radiusSmall(), 5, Theme.shadowColor());
        Render2D.roundedRect(gfx, x, y, WIDTH, height(), Theme.radiusSmall(), Theme.surfaceHigh());

        double fieldX = x + GAP;
        double fieldY = y + GAP;
        double fieldWidth = WIDTH - GAP * 2;

        drawSaturationValueField(gfx, fieldX, fieldY, fieldWidth);

        double sliderY = fieldY + FIELD_HEIGHT + GAP;
        drawHueSlider(gfx, fieldX, sliderY, fieldWidth);

        if (setting.allowsAlpha()) {
            sliderY += SLIDER_HEIGHT + GAP;
            drawAlphaSlider(gfx, fieldX, sliderY, fieldWidth);
        }

        // Hex readout, so a value can be copied out or compared by eye.
        Render2D.text(gfx, font, ColorUtil.toHex(setting.get()), fieldX,
                sliderY + SLIDER_HEIGHT + GAP - 1, Theme.textMuted());
    }

    /**
     * The saturation/value square.
     *
     * <p>Built from vertical strips: each column is a single vertical gradient from the fully
     * saturated hue down to black, which gets both axes from one {@code fillGradient} per
     * column instead of a fill per pixel.
     */
    private void drawSaturationValueField(DrawContext gfx, double x, double y, double width) {
        int columns = (int) Math.ceil(width / FIELD_STEP);
        for (int i = 0; i < columns; i++) {
            double columnX = x + i * FIELD_STEP;
            double columnWidth = Math.min(FIELD_STEP, x + width - columnX);
            float columnSaturation = (float) (i / (double) (columns - 1));

            int top = ColorUtil.hsb(hue, columnSaturation, 1.0f, 255);
            Render2D.gradientV(gfx, columnX, y, columnWidth, FIELD_HEIGHT, top, 0xFF000000);
        }

        Render2D.roundedOutline(gfx, x, y, width, FIELD_HEIGHT, 2, 1.0, Theme.glassEdge());

        // Ring marker rather than a filled dot: a hollow ring at this radius has an inner and
        // outer edge to anti-alias against, where a solid dot this small is just a blob no
        // matter how clean the edge math is.
        double markerX = x + saturation * width;
        double markerY = y + (1.0f - value) * FIELD_HEIGHT;
        int marker = value > 0.55 && saturation < 0.55 ? 0xFF11151C : 0xFFFFFFFF;
        Render2D.circle(gfx, markerX, markerY, 4.5, marker);
        Render2D.circle(gfx, markerX, markerY, 3.2, ColorUtil.hsb(hue, saturation, value, 255));
    }

    private void drawHueSlider(DrawContext gfx, double x, double y, double width) {
        int steps = (int) Math.ceil(width / FIELD_STEP);
        for (int i = 0; i < steps; i++) {
            double stepX = x + i * FIELD_STEP;
            double stepWidth = Math.min(FIELD_STEP, x + width - stepX);
            Render2D.rect(gfx, stepX, y, stepWidth, SLIDER_HEIGHT,
                    ColorUtil.hsb((float) (i / (double) steps), 1.0f, 1.0f, 255));
        }
        drawSliderKnob(gfx, x + hue * width, y);
    }

    private void drawAlphaSlider(DrawContext gfx, double x, double y, double width) {
        // Checkerboard so transparency is legible rather than reading as a dark colour.
        for (int i = 0; i * 4 < width; i++) {
            double cellX = x + i * 4;
            double cellWidth = Math.min(4, x + width - cellX);
            Render2D.rect(gfx, cellX, y, cellWidth, SLIDER_HEIGHT / 2,
                    i % 2 == 0 ? 0xFF9AA5B8 : 0xFF636E82);
            Render2D.rect(gfx, cellX, y + SLIDER_HEIGHT / 2, cellWidth, SLIDER_HEIGHT / 2,
                    i % 2 == 0 ? 0xFF636E82 : 0xFF9AA5B8);
        }

        int opaque = ColorUtil.withAlpha(ColorUtil.hsb(hue, saturation, value, 255), 255);
        Render2D.gradientH(gfx, x, y, width, SLIDER_HEIGHT, ColorUtil.withAlpha(opaque, 0), opaque);
        drawSliderKnob(gfx, x + (alpha / 255.0) * width, y);
    }

    private void drawSliderKnob(DrawContext gfx, double centerX, double y) {
        Render2D.rect(gfx, centerX - 1.5, y - 1, 3, SLIDER_HEIGHT + 2, 0xFFFFFFFF);
        Render2D.rect(gfx, centerX - 0.5, y, 1, SLIDER_HEIGHT, 0xFF11151C);
    }

    // -- input -------------------------------------------------------------------------

    /**
     * Routes a click to whichever control it landed on.
     *
     * @return whether the click was inside the picker
     */
    public boolean mouseClicked(double x, double y, double mouseX, double mouseY) {
        double fieldX = x + GAP;
        double fieldY = y + GAP;
        double fieldWidth = WIDTH - GAP * 2;

        if (MathUtil.within(mouseX, mouseY, fieldX, fieldY, fieldX + fieldWidth, fieldY + FIELD_HEIGHT)) {
            grab = Grab.FIELD;
            updateField(fieldX, fieldY, fieldWidth, mouseX, mouseY);
            return true;
        }

        double hueY = fieldY + FIELD_HEIGHT + GAP;
        if (MathUtil.within(mouseX, mouseY, fieldX, hueY, fieldX + fieldWidth, hueY + SLIDER_HEIGHT)) {
            grab = Grab.HUE;
            updateHue(fieldX, fieldWidth, mouseX);
            return true;
        }

        if (setting.allowsAlpha()) {
            double alphaY = hueY + SLIDER_HEIGHT + GAP;
            if (MathUtil.within(mouseX, mouseY, fieldX, alphaY, fieldX + fieldWidth, alphaY + SLIDER_HEIGHT)) {
                grab = Grab.ALPHA;
                updateAlpha(fieldX, fieldWidth, mouseX);
                return true;
            }
        }

        // Inside the panel but not on a control: still consume, so the click does not fall
        // through to whatever is behind the popup.
        return MathUtil.within(mouseX, mouseY, x, y, x + WIDTH, y + height());
    }

    /** Continues a drag; dragging past the edge clamps rather than releasing. */
    public void mouseDragged(double x, double y, double mouseX, double mouseY) {
        double fieldX = x + GAP;
        double fieldY = y + GAP;
        double fieldWidth = WIDTH - GAP * 2;

        switch (grab) {
            case FIELD -> updateField(fieldX, fieldY, fieldWidth, mouseX, mouseY);
            case HUE -> updateHue(fieldX, fieldWidth, mouseX);
            case ALPHA -> updateAlpha(fieldX, fieldWidth, mouseX);
            case NONE -> {
                // No control was grabbed; nothing to update.
            }
        }
    }

    public void mouseReleased() {
        grab = Grab.NONE;
    }

    public boolean isDragging() {
        return grab != Grab.NONE;
    }

    private void updateField(double fieldX, double fieldY, double fieldWidth, double mouseX, double mouseY) {
        saturation = (float) MathUtil.clamp((mouseX - fieldX) / fieldWidth, 0.0, 1.0);
        value = (float) (1.0 - MathUtil.clamp((mouseY - fieldY) / FIELD_HEIGHT, 0.0, 1.0));
        writeBack();
    }

    private void updateHue(double fieldX, double fieldWidth, double mouseX) {
        hue = (float) MathUtil.clamp((mouseX - fieldX) / fieldWidth, 0.0, 1.0);
        writeBack();
    }

    private void updateAlpha(double fieldX, double fieldWidth, double mouseX) {
        alpha = (int) Math.round(MathUtil.clamp((mouseX - fieldX) / fieldWidth, 0.0, 1.0) * 255);
        writeBack();
    }
}
