package com.gadzo.client.core.hud;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.EnumSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A module that draws something on the in-game HUD.
 *
 * <p>Subclasses supply their content size and paint themselves at an origin the base class
 * computes; anchoring, offsets, scaling, the background plate and the editor's hit box are
 * all handled here so every readout behaves identically under the HUD editor.
 *
 * <p>Position is stored as an {@link HudAnchor} plus a pixel offset rather than as absolute
 * coordinates, so a layout survives a resolution or GUI-scale change.
 */
public abstract class HudModule extends Module {

    private final EnumSetting<HudAnchor> anchor;
    private final NumberSetting scale;
    private final BooleanSetting background;
    private final BooleanSetting textShadow;
    private final BooleanSetting borderEnabled;

    /** Offset from the anchor origin, in unscaled GUI pixels. */
    private double offsetX;
    private double offsetY;

    /** Cached each frame so the editor can hit-test without re-measuring. */
    private double lastX;
    private double lastY;
    private double lastWidth;
    private double lastHeight;

    protected HudModule(String name, String description, HudAnchor defaultAnchor,
                        double defaultOffsetX, double defaultOffsetY) {
        this(name, description, ModuleCategory.HUD, defaultAnchor, defaultOffsetX, defaultOffsetY);
    }

    /**
     * Overload for HUD elements that belong in another menu category.
     *
     * <p>A combo counter, for example, is drawn on the HUD but a player looks for it under
     * Combat. The HUD editor finds elements by type, not by category, so this only affects
     * where the module is listed.
     */
    protected HudModule(String name, String description, ModuleCategory category,
                        HudAnchor defaultAnchor, double defaultOffsetX, double defaultOffsetY) {
        super(name, description, category);
        this.offsetX = defaultOffsetX;
        this.offsetY = defaultOffsetY;
        this.anchor = addEnum("Anchor", defaultAnchor, "Screen corner this element is pinned to");
        this.scale = addNumber("Scale", 1.0, 0.5, 2.5, 0.05, "Size multiplier for this element");
        this.background = addBool("Background", true, "Draw a rounded plate behind the content");
        this.borderEnabled = add(new BooleanSetting("Border", false)
                .<BooleanSetting>describe("Outline the background plate")
                .visibleWhen(() -> this.background.get()));
        this.textShadow = addBool("Text shadow", true, "Drop shadow behind the text");
    }

    // -- geometry ---------------------------------------------------------------------

    /** Width of the content, before scaling and padding. */
    public abstract double contentWidth(TextRenderer font);

    /** Height of the content, before scaling and padding. */
    public abstract double contentHeight(TextRenderer font);

    /** Paints the content with its top-left corner at the origin of the current transform. */
    protected abstract void renderContent(DrawContext gfx, TextRenderer font);

    /** Inner padding around the content when the background plate is drawn. */
    protected double padding() {
        return background.get() ? 4.0 : 0.0;
    }

    public double getScale() {
        return scale.get();
    }

    public HudAnchor getAnchor() {
        return anchor.get();
    }

    public void setAnchor(HudAnchor value) {
        anchor.setValue(value);
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public void setOffset(double x, double y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    public boolean hasTextShadow() {
        return textShadow.get();
    }

    /** Total on-screen width including padding and scale. */
    public double totalWidth(TextRenderer font) {
        return (contentWidth(font) + padding() * 2) * getScale();
    }

    public double totalHeight(TextRenderer font) {
        return (contentHeight(font) + padding() * 2) * getScale();
    }

    /**
     * Resolved top-left x on a screen of the given width.
     *
     * <p>The element is shifted by its own size scaled by the anchor factor, so a
     * right-anchored element grows leftwards instead of off the screen edge.
     */
    public double resolveX(TextRenderer font, int screenWidth) {
        HudAnchor a = getAnchor();
        return a.originX(screenWidth) + offsetX - totalWidth(font) * a.xFactor();
    }

    public double resolveY(TextRenderer font, int screenHeight) {
        HudAnchor a = getAnchor();
        return a.originY(screenHeight) + offsetY - totalHeight(font) * a.yFactor();
    }

    /**
     * Re-derives the offset so the element's top-left lands on an absolute point.
     *
     * <p>The HUD editor calls this while dragging; the anchor stays fixed during the drag and
     * is only re-picked on release.
     */
    public void moveTopLeftTo(TextRenderer font, double x, double y, int screenWidth, int screenHeight) {
        HudAnchor a = getAnchor();
        this.offsetX = x - a.originX(screenWidth) + totalWidth(font) * a.xFactor();
        this.offsetY = y - a.originY(screenHeight) + totalHeight(font) * a.yFactor();
    }

    /**
     * Re-anchors to the nearest corner while keeping the element visually still.
     *
     * <p>Called when a drag ends so the element picks up sensible resize behaviour without
     * appearing to jump.
     */
    public void reanchor(TextRenderer font, int screenWidth, int screenHeight) {
        double x = lastX;
        double y = lastY;
        double centerX = x + totalWidth(font) / 2.0;
        double centerY = y + totalHeight(font) / 2.0;
        setAnchor(HudAnchor.nearest(centerX, centerY, screenWidth, screenHeight));
        moveTopLeftTo(font, x, y, screenWidth, screenHeight);
    }

    /** Keeps the element fully on screen after a resolution change. */
    public void clampToScreen(TextRenderer font, int screenWidth, int screenHeight) {
        double width = totalWidth(font);
        double height = totalHeight(font);
        double x = MathUtil.clamp(resolveX(font, screenWidth), 0, Math.max(0, screenWidth - width));
        double y = MathUtil.clamp(resolveY(font, screenHeight), 0, Math.max(0, screenHeight - height));
        moveTopLeftTo(font, x, y, screenWidth, screenHeight);
    }

    // -- last-frame bounds, for the editor ---------------------------------------------

    public double lastX() {
        return lastX;
    }

    public double lastY() {
        return lastY;
    }

    public double lastWidth() {
        return lastWidth;
    }

    public double lastHeight() {
        return lastHeight;
    }

    public boolean containsPoint(double x, double y) {
        return MathUtil.within(x, y, lastX, lastY, lastX + lastWidth, lastY + lastHeight);
    }

    // -- rendering ----------------------------------------------------------------------

    /**
     * Draws the element at its resolved position.
     *
     * @param editorAlpha extra fade applied by the HUD editor; 1 during normal play
     */
    public void render(DrawContext gfx, TextRenderer font, int screenWidth, int screenHeight, double editorAlpha) {
        double width = totalWidth(font);
        double height = totalHeight(font);
        double x = resolveX(font, screenWidth);
        double y = resolveY(font, screenHeight);

        this.lastX = x;
        this.lastY = y;
        this.lastWidth = width;
        this.lastHeight = height;

        // An element with nothing to say reports zero content, which still leaves the padding
        // on both axes — enough for the background plate to draw as a small floating square.
        // Elements that hide themselves conditionally (Target with no target, Durability with
        // nothing worn) are common enough that this is worth catching here rather than in each.
        if (contentWidth(font) <= 0 && contentHeight(font) <= 0) {
            this.lastWidth = 0;
            this.lastHeight = 0;
            return;
        }

        double s = getScale();
        double pad = padding();

        if (background.get()) {
            int plate = com.gadzo.client.util.ColorUtil.fade(Theme.background(), 0.82 * editorAlpha);
            Render2D.roundedRect(gfx, x, y, width, height, Theme.radiusSmall(), plate);
            if (borderEnabled.get()) {
                Render2D.roundedOutline(gfx, x, y, width, height, Theme.radiusSmall(), 1.0,
                        com.gadzo.client.util.ColorUtil.fade(Theme.accent(), editorAlpha));
            }
        }

        gfx.getMatrices().push();
        gfx.getMatrices().translate((float) (x + pad * s), (float) (y + pad * s), 0.0f);
        if (s != 1.0) {
            gfx.getMatrices().scale((float) s, (float) s, 1.0f);
        }
        renderContent(gfx, font);
        gfx.getMatrices().pop();
    }

    /** Convenience for subclasses: draws a line of text honouring the shadow setting. */
    protected void line(DrawContext gfx, TextRenderer font, String value, double x, double y, int color) {
        gfx.drawText(font, value, (int) Math.floor(x), (int) Math.floor(y), color, hasTextShadow());
    }
}
