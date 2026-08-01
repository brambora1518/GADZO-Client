package com.gadzo.client.ui.screen;

import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.Easing;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The chrome every GADZO panel shares.
 *
 * <p>One frosted surface with exactly one edge on it. Inside there are no panels, no cards and
 * no boxes: sections are separated by space and by type weight, never by a border. That rule is
 * the whole design — an interface reads as glass when light passes through a single sheet, and
 * as a filing cabinet the moment a second rectangle is drawn on top of the first.
 *
 * <p>Navigation is a row of text tabs with an accent underline rather than a sidebar. A sidebar
 * costs a fifth of the window permanently, adds a vertical edge through the middle of the
 * surface, and buys nothing that four words along the top do not.
 *
 * <p>Subclasses supply the tab names, the body, and the hint line. Everything else — blur,
 * entrance motion, scrolling, clipping, tab hit-testing, keyboard navigation — is handled here,
 * so two screens cannot drift apart the way the mods menu and the helpers did.
 */
public abstract class GlassScreen extends Screen {

    protected static final double HEADER_HEIGHT = 44;
    protected static final double TABS_HEIGHT = 32;
    protected static final double HINT_HEIGHT = 26;
    protected static final double PADDING = 16;

    /** Gap between tab labels. Wide enough that the underline never looks shared. */
    private static final double TAB_GAP = 20;

    /** Vertical placement, as a fraction of the leftover space. Slightly above centre. */
    private static final double PANEL_TOP_BIAS = 0.40;

    private final Animation openAnimation = new Animation(0.0, 300L, Easing.EXPO_OUT);
    private final Map<Integer, Animation> tabHover = new HashMap<>();
    private final Map<Integer, Animation> tabSelect = new HashMap<>();

    protected int activeTab;
    protected String searchQuery = "";
    protected double scroll;

    /** Set by the body each frame; the base class uses it to clamp scrolling. */
    protected double contentHeight;

    protected double panelX;
    protected double panelY;

    /** Entrance alpha for the current frame, so body code can fade with the panel. */
    protected double openAlpha = 1.0;

    protected GlassScreen(String title) {
        super(Text.literal(title));
    }

    // -- what subclasses provide ----------------------------------------------------------

    /** Tab labels, left to right. */
    protected abstract List<String> tabs();

    /**
     * Draws the active tab's content.
     *
     * <p>Called inside a scissor covering the body, with scrolling already applied via
     * {@link #bodyTop()}. Implementations set {@link #contentHeight} to the height they used.
     */
    protected abstract void drawBody(DrawContext gfx, int mouseX, int mouseY);

    /** Left-hand text of the hint row. */
    protected abstract String hint();

    /** Right-hand text of the hint row, or {@code null}. */
    protected String hintRight() {
        return null;
    }

    /** Whether typing filters the current tab. */
    protected boolean searchable() {
        return false;
    }

    /** Placeholder shown in the header before anything is typed. */
    protected String searchPlaceholder() {
        return "Type to filter";
    }

    /** Right-hand header text used when the tab is not searchable. */
    protected String headerRight() {
        return null;
    }

    protected double panelWidth() {
        return 520;
    }

    protected double panelHeight() {
        return 420;
    }

    /** Called when the tab changes, so subclasses can reset per-tab state. */
    protected void onTabChanged(int tab) {
    }

    // -- geometry ---------------------------------------------------------------------------

    protected double contentX() {
        return panelX + PADDING;
    }

    protected double contentWidth() {
        return panelWidth() - PADDING * 2;
    }

    protected double bodyTop() {
        return panelY + HEADER_HEIGHT + TABS_HEIGHT;
    }

    protected double bodyBottom() {
        return panelY + panelHeight() - HINT_HEIGHT;
    }

    /** First drawing line of the body, with the scroll offset already applied. */
    protected double bodyOrigin() {
        return bodyTop() + 10 - scroll;
    }

    @Override
    protected void init() {
        openAnimation.to(1.0);
        panelX = (width - panelWidth()) / 2.0;
        panelY = (height - panelHeight()) * PANEL_TOP_BIAS;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Animation hoverOf(int index) {
        return tabHover.computeIfAbsent(index, ignored -> new Animation(0.0, 140L));
    }

    private Animation selectOf(int index) {
        return tabSelect.computeIfAbsent(index, ignored -> new Animation(0.0, 220L, Easing.EXPO_OUT));
    }

    // -- rendering ---------------------------------------------------------------------------

    @Override
    public void render(DrawContext gfx, int mouseX, int mouseY, float partialTick) {
        double open = openAnimation.value();
        openAlpha = open;

        boolean blurred = Theme.blurEnabled() && Render2D.blurBehind(gfx);
        Render2D.rect(gfx, 0, 0, width, height,
                ColorUtil.fade(blurred ? 0x38000000 : 0xB0000000, open));

        panelX = (width - panelWidth()) / 2.0;
        panelY = (height - panelHeight()) * PANEL_TOP_BIAS;

        gfx.getMatrices().push();
        gfx.getMatrices().translate(0.0f, (float) ((1.0 - open) * 14.0), 0.0f);

        Render2D.shadow(gfx, panelX, panelY, panelWidth(), panelHeight(), Theme.radius(), 12,
                ColorUtil.fade(Theme.shadowColor(), open));
        Render2D.roundedRect(gfx, panelX, panelY, panelWidth(), panelHeight(), Theme.radius(),
                ColorUtil.fade(Theme.background(), open));

        drawHeader(gfx);
        drawTabs(gfx, mouseX, mouseY);

        Render2D.pushScissor(gfx, panelX, bodyTop(), panelWidth(), bodyBottom() - bodyTop());
        drawBody(gfx, mouseX, mouseY);
        Render2D.popScissor(gfx);

        drawHint(gfx);

        // The panel's only boundary.
        Render2D.roundedOutline(gfx, panelX, panelY, panelWidth(), panelHeight(), Theme.radius(),
                1.0, ColorUtil.fade(Theme.glassEdge(), open));

        gfx.getMatrices().pop();
    }

    private void drawHeader(DrawContext gfx) {
        double textY = panelY + (HEADER_HEIGHT - textRenderer.fontHeight) / 2.0;
        int muted = ColorUtil.fade(Theme.textMuted(), openAlpha);

        Render2D.text(gfx, textRenderer, getTitle().getString(), contentX(), textY,
                ColorUtil.fade(Theme.textPrimary(), openAlpha));

        if (searchable()) {
            drawSearch(gfx, textY, muted);
        } else {
            String right = headerRight();
            if (right != null) {
                Render2D.textRight(gfx, textRenderer, right,
                        panelX + panelWidth() - PADDING, textY, muted, false);
            }
        }
    }

    /**
     * The filter field, right-aligned in the header.
     *
     * <p>Always focused, like the mods menu — there is nothing else on these screens that
     * wants the keyboard, so a focus state would be a mode with only one setting. The caret
     * is the only thing marking it as an input; a box around it would be one rectangle too
     * many.
     */
    private void drawSearch(DrawContext gfx, double textY, int muted) {
        double right = panelX + panelWidth() - PADDING;
        String shown = searchQuery.isEmpty() ? searchPlaceholder() : searchQuery;
        double textWidth = textRenderer.getWidth(shown);

        double caretX = right;
        if (!searchQuery.isEmpty()) {
            caretX = right + 2;
        }
        Render2D.textRight(gfx, textRenderer, shown, right, textY,
                searchQuery.isEmpty() ? muted : ColorUtil.fade(Theme.textPrimary(), openAlpha), false);

        double glassX = right - textWidth - 14;
        Render2D.circle(gfx, glassX, panelY + HEADER_HEIGHT / 2.0 - 1, 4.0, muted);
        Render2D.circle(gfx, glassX, panelY + HEADER_HEIGHT / 2.0 - 1, 2.7,
                ColorUtil.fade(Theme.background(), openAlpha));
        Render2D.roundedRect(gfx, glassX + 2.6, panelY + HEADER_HEIGHT / 2.0 + 1.6, 4.0, 1.5, 0.75, muted);

        if (!searchQuery.isEmpty() && (System.currentTimeMillis() / 530) % 2 == 0) {
            Render2D.rect(gfx, caretX, textY - 2, 1, textRenderer.fontHeight + 4,
                    ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 210), openAlpha));
        }
    }

    /**
     * The tab strip: text only, with an accent underline beneath the active label.
     *
     * <p>The underline animates its width from the label's centre, which is what makes the
     * strip read as one control rather than as several buttons that happen to be adjacent.
     */
    private void drawTabs(DrawContext gfx, int mouseX, int mouseY) {
        List<String> labels = tabs();
        double y = panelY + HEADER_HEIGHT;
        double textY = y + (TABS_HEIGHT - textRenderer.fontHeight) / 2.0 - 1;
        double x = contentX();

        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            double labelWidth = textRenderer.getWidth(label);
            boolean hovered = MathUtil.within(mouseX, mouseY, x - 4, y, x + labelWidth + 4, y + TABS_HEIGHT);
            boolean active = i == activeTab;

            hoverOf(i).toBoolean(hovered);
            selectOf(i).toBoolean(active);

            int color = active
                    ? Theme.textPrimary()
                    : ColorUtil.mix(Theme.textMuted(), Theme.textSecondary(), hoverOf(i).value());
            Render2D.text(gfx, textRenderer, label, x, textY, ColorUtil.fade(color, openAlpha));

            double grown = selectOf(i).value();
            if (grown > 0.01) {
                double underlineWidth = labelWidth * grown;
                Render2D.roundedRect(gfx,
                        x + (labelWidth - underlineWidth) / 2.0, y + TABS_HEIGHT - 7,
                        underlineWidth, 2.0, 1.0,
                        ColorUtil.fade(Theme.accent(), openAlpha * grown));
            }

            x += labelWidth + TAB_GAP;
        }

        Render2D.rect(gfx, contentX(), y + TABS_HEIGHT - 1, contentWidth(), 1,
                ColorUtil.fade(Theme.glassEdge(), openAlpha * 0.7));
    }

    private void drawHint(DrawContext gfx) {
        double y = panelY + panelHeight() - HINT_HEIGHT;
        Render2D.rect(gfx, contentX(), y, contentWidth(), 1,
                ColorUtil.fade(Theme.glassEdge(), openAlpha * 0.6));

        double textY = y + (HINT_HEIGHT - textRenderer.fontHeight) / 2.0 + 1;
        int muted = ColorUtil.fade(Theme.textMuted(), openAlpha);
        Render2D.text(gfx, textRenderer, hint(), contentX(), textY, muted);

        String right = hintRight();
        if (right != null) {
            Render2D.textRight(gfx, textRenderer, right,
                    panelX + panelWidth() - PADDING, textY, muted, false);
        }
    }

    // -- shared body drawing ------------------------------------------------------------------

    /**
     * A section heading.
     *
     * <p>Accent-coloured and small rather than boxed or ruled. Weight and colour separate the
     * sections; a rule across the panel would be another edge.
     */
    protected void sectionLabel(DrawContext gfx, String label, double x, double y) {
        Render2D.text(gfx, textRenderer, label, x, y,
                ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 235), openAlpha));
    }

    /** A label on the left and its value right-aligned to {@code right}. */
    protected void keyValue(DrawContext gfx, String key, String value, double x, double right, double y) {
        Render2D.text(gfx, textRenderer, key, x, y, ColorUtil.fade(Theme.textSecondary(), openAlpha));
        Render2D.textRight(gfx, textRenderer, value, right, y,
                ColorUtil.fade(Theme.textPrimary(), openAlpha), false);
    }

    /** Body copy. */
    protected void body(DrawContext gfx, String value, double x, double y) {
        Render2D.text(gfx, textRenderer, value, x, y, ColorUtil.fade(Theme.textSecondary(), openAlpha));
    }

    /** A filled progress bar with a translucent track. */
    protected void bar(DrawContext gfx, double x, double y, double width, double height,
                       double fraction, int color) {
        Render2D.roundedRect(gfx, x, y, width, height, height / 2.0,
                ColorUtil.fade(Theme.trackOff(), openAlpha));
        double filled = MathUtil.clamp(fraction, 0.0, 1.0) * width;
        if (filled > 0.5) {
            Render2D.roundedRect(gfx, x, y, filled, height, height / 2.0,
                    ColorUtil.fade(color, openAlpha));
        }
    }

    /** Greedy word wrap, shared so the two helper screens cannot disagree about it. */
    protected List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (textRenderer.getWidth(candidate) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    // -- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double clickX, double clickY, int button) {
        List<String> labels = tabs();
        double y = panelY + HEADER_HEIGHT;
        double x = contentX();
        for (int i = 0; i < labels.size(); i++) {
            double labelWidth = textRenderer.getWidth(labels.get(i));
            if (MathUtil.within(clickX, clickY, x - 4, y, x + labelWidth + 4, y + TABS_HEIGHT)) {
                selectTab(i);
                return true;
            }
            x += labelWidth + TAB_GAP;
        }
        return super.mouseClicked(clickX, clickY, button);
    }

    protected void selectTab(int index) {
        if (index == activeTab) {
            return;
        }
        activeTab = index;
        scroll = 0;
        onTabChanged(index);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        double viewport = bodyBottom() - bodyTop();
        double max = Math.max(0, contentHeight - viewport + 16);
        scroll = MathUtil.clamp(scroll - vertical * 22, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> {
                selectTab(Math.max(0, activeTab - 1));
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                selectTab(Math.min(tabs().size() - 1, activeTab + 1));
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                selectTab((activeTab + 1) % tabs().size());
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (searchable() && !searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    scroll = 0;
                }
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                // Back out one layer at a time, as the mods menu does.
                if (searchable() && !searchQuery.isEmpty()) {
                    searchQuery = "";
                    scroll = 0;
                    return true;
                }
            }
            default -> {
                // Fall through.
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!searchable()) {
            return false;
        }
        searchQuery += chr;
        scroll = 0;
        return true;
    }
}
