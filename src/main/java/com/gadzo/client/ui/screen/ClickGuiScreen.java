package com.gadzo.client.ui.screen;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.setting.Setting;
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
 * The mods menu, as a command palette.
 *
 * <p>One narrow column: a search field that is always focused, a flat list of every module in
 * the client, and — for whichever row is expanded — that module's settings inline underneath
 * it. There is no category sidebar and no separate settings pane.
 *
 * <p>That shape was chosen over the usual three-column layout deliberately. With thirty-odd
 * modules, picking a category and then hunting a list is strictly more work than typing three
 * letters, and the sidebar spends its whole life occupying a fifth of the window to save one
 * keystroke. Search-first also means the same screen scales to a hundred modules without
 * changing, and it removes the two internal panel edges that made the old layout read as a
 * stack of boxes rather than a single surface.
 *
 * <p>Everything is keyboard-reachable: type to filter, arrows to move, enter to toggle, tab to
 * expand settings. The mouse does all of it too, but the keyboard path is the fast one and the
 * hint row at the bottom says so.
 */
public class ClickGuiScreen extends Screen {

    private static final double PANEL_WIDTH = 430;
    private static final double PANEL_HEIGHT = 396;
    private static final double SEARCH_HEIGHT = 44;
    private static final double ROW_HEIGHT = 30;
    private static final double HINT_HEIGHT = 26;
    private static final double PADDING = 16;

    /** Vertical placement, as a fraction of the leftover space. Slightly above centre. */
    private static final double PANEL_TOP_BIAS = 0.40;

    private final Animation openAnimation = new Animation(0.0, 300L, Easing.EXPO_OUT);
    private final Map<Module, Animation> rowHighlight = new HashMap<>();

    /** The row whose settings are expanded, or {@code null}. */
    private Module expanded;

    /** Keyboard cursor, an index into {@link #visibleModules()}. */
    private int cursor;

    private String searchQuery = "";
    private double scroll;

    /** Total content height from the last frame, for scroll clamping. */
    private double contentHeight;

    /** Cached each frame so input handlers agree with what was drawn. */
    private double panelX;
    private double panelY;

    public ClickGuiScreen() {
        super(Text.literal("GADZO"));
    }

    @Override
    protected void init() {
        openAnimation.to(1.0);
        panelX = (width - PANEL_WIDTH) / 2.0;
        panelY = (height - PANEL_HEIGHT) * PANEL_TOP_BIAS;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // -- model ----------------------------------------------------------------------------

    /**
     * The modules on screen, in registration order, filtered by the search.
     *
     * <p>Matching is delegated to the module manager so the palette and the {@code /gadzo}
     * commands agree on what a query means.
     */
    private List<Module> visibleModules() {
        List<Module> matches = searchQuery.isBlank()
                ? GadzoClient.modules().all()
                : GadzoClient.modules().search(searchQuery);

        List<Module> visible = new ArrayList<>(matches.size());
        for (Module module : matches) {
            if (!module.isHidden()) {
                visible.add(module);
            }
        }
        return visible;
    }

    private Animation highlightOf(Module module) {
        return rowHighlight.computeIfAbsent(module, ignored -> new Animation(0.0, 160L));
    }

    private double listTop() {
        return panelY + SEARCH_HEIGHT;
    }

    private double listBottom() {
        return panelY + PANEL_HEIGHT - HINT_HEIGHT;
    }

    /**
     * Y of a row, accounting for the expanded module's settings pushing later rows down.
     *
     * <p>Shared by the renderer and every hit test. Computing it twice is how a list ends up
     * highlighting one row and toggling another.
     */
    private double rowY(List<Module> modules, int index) {
        double y = listTop() + 6 - scroll;
        for (int i = 0; i < index; i++) {
            y += ROW_HEIGHT;
            if (modules.get(i) == expanded) {
                y += settingsHeight(modules.get(i));
            }
        }
        return y;
    }

    /** Height the expanded settings block occupies, including its own padding. */
    private double settingsHeight(Module module) {
        double height = 4;
        height += SettingRenderer.ROW_HEIGHT + SettingRenderer.extraHeight(module.getKeybind(), textRenderer);
        for (Setting<?> setting : module.getVisibleSettings()) {
            height += SettingRenderer.ROW_HEIGHT + SettingRenderer.extraHeight(setting, textRenderer);
        }
        return height + 6;
    }

    // -- rendering ------------------------------------------------------------------------

    @Override
    public void render(DrawContext gfx, int mouseX, int mouseY, float partialTick) {
        double open = openAnimation.value();
        boolean blurred = Theme.blurEnabled() && Render2D.blurBehind(gfx);
        Render2D.rect(gfx, 0, 0, width, height,
                ColorUtil.fade(blurred ? 0x38000000 : 0xB0000000, open));

        panelX = (width - PANEL_WIDTH) / 2.0;
        panelY = (height - PANEL_HEIGHT) * PANEL_TOP_BIAS;

        gfx.getMatrices().push();
        gfx.getMatrices().translate(0.0f, (float) ((1.0 - open) * 14.0), 0.0f);

        Render2D.shadow(gfx, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, Theme.radius(), 12,
                ColorUtil.fade(Theme.shadowColor(), open));
        Render2D.roundedRect(gfx, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, Theme.radius(),
                ColorUtil.fade(Theme.background(), open));

        drawSearch(gfx, open);
        drawList(gfx, mouseX, mouseY, open);
        drawHint(gfx, open);

        // The panel's single boundary: a hairline on its own outer edge. Nothing inside draws
        // an outline of its own, which is what keeps this reading as one surface.
        Render2D.roundedOutline(gfx, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, Theme.radius(),
                1.0, ColorUtil.fade(Theme.glassEdge(), open));

        gfx.getMatrices().pop();
    }

    private void drawSearch(DrawContext gfx, double alpha) {
        double textY = panelY + (SEARCH_HEIGHT - textRenderer.fontHeight) / 2.0;
        int muted = ColorUtil.fade(Theme.textMuted(), alpha);

        // A magnifier built from two circles and a bar — the client ships no texture atlas,
        // and a glyph from the font would not sit right next to the text at this size.
        double glassX = panelX + PADDING + 5;
        double glassY = panelY + SEARCH_HEIGHT / 2.0 - 1;
        Render2D.circle(gfx, glassX, glassY, 4.5, muted);
        Render2D.circle(gfx, glassX, glassY, 3.0, ColorUtil.fade(Theme.background(), alpha));
        Render2D.roundedRect(gfx, glassX + 3, glassY + 3, 4.5, 1.6, 0.8, muted);

        double textX = panelX + PADDING + 20;
        String shown = searchQuery.isEmpty() ? "Search modules" : searchQuery;
        Render2D.text(gfx, textRenderer, shown, textX, textY,
                searchQuery.isEmpty() ? muted : ColorUtil.fade(Theme.textPrimary(), alpha));

        // The field is always focused, so the caret is always drawn; it is the only cue that
        // typing goes here, now that there is no box around it.
        if ((System.currentTimeMillis() / 530) % 2 == 0) {
            double caretX = textX + (searchQuery.isEmpty() ? 0 : textRenderer.getWidth(searchQuery) + 2);
            Render2D.rect(gfx, caretX, panelY + 14, 1, 16,
                    ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 210), alpha));
        }

        int count = visibleModules().size();
        Render2D.textRight(gfx, textRenderer, Integer.toString(count),
                panelX + PANEL_WIDTH - PADDING, textY, muted, false);

        Render2D.rect(gfx, panelX + PADDING, panelY + SEARCH_HEIGHT, PANEL_WIDTH - PADDING * 2, 1,
                ColorUtil.fade(Theme.glassEdge(), alpha));
    }

    private void drawList(DrawContext gfx, int mouseX, int mouseY, double alpha) {
        List<Module> modules = visibleModules();
        cursor = MathUtil.clamp(cursor, 0, Math.max(0, modules.size() - 1));

        Render2D.pushScissor(gfx, panelX, listTop(), PANEL_WIDTH, listBottom() - listTop());

        if (modules.isEmpty()) {
            Render2D.textCentered(gfx, textRenderer, "No modules match that search",
                    panelX + PANEL_WIDTH / 2.0, listTop() + 24,
                    ColorUtil.fade(Theme.textMuted(), alpha), false);
        }

        double lastY = listTop();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            double y = rowY(modules, i);
            lastY = y + ROW_HEIGHT;

            // Skip rows scrolled fully out of view, but keep walking so later ones land right.
            if (y + ROW_HEIGHT >= listTop() - 4 && y <= listBottom() + 4) {
                drawRow(gfx, module, y, i, mouseX, mouseY, alpha);
            }
            if (module == expanded) {
                double settingsTop = y + ROW_HEIGHT;
                drawSettings(gfx, module, settingsTop, mouseX, mouseY);
                lastY = settingsTop + settingsHeight(module);
            }
        }

        Render2D.popScissor(gfx);
        contentHeight = lastY + scroll - listTop();
    }

    private void drawRow(DrawContext gfx, Module module, double y, int index,
                         int mouseX, int mouseY, double alpha) {
        boolean hovered = MathUtil.within(mouseX, mouseY, panelX, y, panelX + PANEL_WIDTH, y + ROW_HEIGHT);
        boolean isCursor = index == cursor;
        boolean isExpanded = module == expanded;

        Animation highlight = highlightOf(module);
        highlight.toBoolean(hovered || isCursor || isExpanded);

        if (highlight.value() > 0.01) {
            int tint = (isCursor || isExpanded)
                    ? ColorUtil.withAlpha(Theme.accent(), 28)
                    : Theme.surfaceHover();
            Render2D.roundedRect(gfx, panelX + 6, y, PANEL_WIDTH - 12, ROW_HEIGHT,
                    Theme.radiusSmall(), ColorUtil.fade(tint, highlight.value() * alpha));
        }
        if (isCursor || isExpanded) {
            Render2D.roundedRect(gfx, panelX + 6, y + 8, 2.5, ROW_HEIGHT - 16, 1.25,
                    ColorUtil.fade(Theme.accent(), alpha));
        }

        double textY = y + (ROW_HEIGHT - textRenderer.fontHeight) / 2.0;
        double dotX = panelX + PANEL_WIDTH - PADDING - 4;

        String category = module.getCategory().displayName();
        double categoryRight = dotX - 14;
        double nameBudget = categoryRight - (panelX + PADDING + 4) - textRenderer.getWidth(category) - 12;

        Render2D.text(gfx, textRenderer,
                Render2D.truncate(textRenderer, module.getName(), (int) nameBudget),
                panelX + PADDING + 4, textY,
                ColorUtil.fade(module.isEnabled() ? Theme.textPrimary() : Theme.textSecondary(), alpha));

        Render2D.textRight(gfx, textRenderer, category, categoryRight, textY,
                ColorUtil.fade(Theme.textMuted(), alpha), false);

        // State is a dot, not a switch. Thirty switches in a column is a wall of controls;
        // a dot reads as status, and the row itself is the control.
        int dot = module.isEnabled()
                ? Theme.accent()
                : ColorUtil.withAlpha(Theme.textMuted(), module.isPermanent() ? 40 : 95);
        Render2D.circle(gfx, dotX, y + ROW_HEIGHT / 2.0, 3.2, ColorUtil.fade(dot, alpha));
    }

    private void drawSettings(DrawContext gfx, Module module, double top, int mouseX, int mouseY) {
        double x = panelX + PADDING + 14;
        double innerWidth = PANEL_WIDTH - PADDING * 2 - 18;
        double y = top + 4;

        SettingRenderer.render(gfx, textRenderer, module.getKeybind(), x, y, innerWidth, mouseX, mouseY);
        y += SettingRenderer.ROW_HEIGHT + SettingRenderer.extraHeight(module.getKeybind(), textRenderer);

        for (Setting<?> setting : module.getVisibleSettings()) {
            SettingRenderer.render(gfx, textRenderer, setting, x, y, innerWidth, mouseX, mouseY);
            y += SettingRenderer.ROW_HEIGHT + SettingRenderer.extraHeight(setting, textRenderer);
        }
    }

    private void drawHint(DrawContext gfx, double alpha) {
        double y = panelY + PANEL_HEIGHT - HINT_HEIGHT;
        Render2D.rect(gfx, panelX + PADDING, y, PANEL_WIDTH - PADDING * 2, 1,
                ColorUtil.fade(Theme.glassEdge(), alpha * 0.6));

        double textY = y + (HINT_HEIGHT - textRenderer.fontHeight) / 2.0 + 1;
        Render2D.text(gfx, textRenderer, "enter toggle    tab settings    esc close",
                panelX + PADDING, textY, ColorUtil.fade(Theme.textMuted(), alpha));
        Render2D.textRight(gfx, textRenderer,
                GadzoClient.modules().enabledCount() + " on",
                panelX + PANEL_WIDTH - PADDING, textY,
                ColorUtil.fade(Theme.textMuted(), alpha), false);
    }

    // -- input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double clickX, double clickY, int button) {
        List<Module> modules = visibleModules();

        // An expanded module's settings get first refusal: a dropdown or colour picker can
        // extend over the rows beneath it and has to consume the click before they do.
        if (expanded != null) {
            double settingsX = panelX + PADDING + 14;
            double innerWidth = PANEL_WIDTH - PADDING * 2 - 18;
            int index = modules.indexOf(expanded);
            if (index >= 0) {
                double y = rowY(modules, index) + ROW_HEIGHT + 4;

                if (SettingRenderer.mouseClicked(expanded.getKeybind(), textRenderer, settingsX, y,
                        innerWidth, clickX, clickY, button)) {
                    return true;
                }
                y += SettingRenderer.ROW_HEIGHT
                        + SettingRenderer.extraHeight(expanded.getKeybind(), textRenderer);

                for (Setting<?> setting : expanded.getVisibleSettings()) {
                    if (SettingRenderer.mouseClicked(setting, textRenderer, settingsX, y,
                            innerWidth, clickX, clickY, button)) {
                        return true;
                    }
                    y += SettingRenderer.ROW_HEIGHT
                            + SettingRenderer.extraHeight(setting, textRenderer);
                }
            }
        }

        for (int i = 0; i < modules.size(); i++) {
            double y = rowY(modules, i);
            if (!MathUtil.within(clickX, clickY, panelX, y, panelX + PANEL_WIDTH, y + ROW_HEIGHT)) {
                continue;
            }
            Module module = modules.get(i);
            cursor = i;
            if (button == 1) {
                toggleExpanded(module);
            } else {
                module.toggle();
            }
            return true;
        }
        return super.mouseClicked(clickX, clickY, button);
    }

    private void toggleExpanded(Module module) {
        SettingRenderer.closePopups();
        expanded = expanded == module ? null : module;
    }

    @Override
    public boolean mouseDragged(double clickX, double clickY, int button, double deltaX, double deltaY) {
        if (SettingRenderer.isDragging()) {
            SettingRenderer.mouseDragged(panelX + PADDING + 14, PANEL_WIDTH - PADDING * 2 - 18,
                    clickX, clickY);
            return true;
        }
        return super.mouseDragged(clickX, clickY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double clickX, double clickY, int button) {
        SettingRenderer.releaseDrag();
        return super.mouseReleased(clickX, clickY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        double viewport = listBottom() - listTop();
        double max = Math.max(0, contentHeight - viewport + 12);
        scroll = MathUtil.clamp(scroll - vertical * 22, 0, max);
        return true;
    }

    /** Keeps the keyboard cursor inside the viewport after an arrow-key move. */
    private void scrollToCursor() {
        List<Module> modules = visibleModules();
        if (modules.isEmpty()) {
            return;
        }
        double y = rowY(modules, cursor);
        if (y < listTop() + 4) {
            scroll -= (listTop() + 4) - y;
        } else if (y + ROW_HEIGHT > listBottom() - 4) {
            scroll += (y + ROW_HEIGHT) - (listBottom() - 4);
        }
        scroll = Math.max(0, scroll);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // A listening keybind row swallows every key, including escape, so it can be cleared.
        if (expanded != null) {
            if (SettingRenderer.keyPressed(expanded.getKeybind(), keyCode)) {
                return true;
            }
            for (Setting<?> setting : expanded.getVisibleSettings()) {
                if (SettingRenderer.keyPressed(setting, keyCode)) {
                    return true;
                }
            }
        }

        List<Module> modules = visibleModules();
        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> {
                cursor = Math.min(cursor + 1, Math.max(0, modules.size() - 1));
                scrollToCursor();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                cursor = Math.max(0, cursor - 1);
                scrollToCursor();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (cursor < modules.size()) {
                    modules.get(cursor).toggle();
                }
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (cursor < modules.size()) {
                    toggleExpanded(modules.get(cursor));
                }
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    cursor = 0;
                    scroll = 0;
                }
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                // Escape backs out one layer at a time rather than closing outright: clear the
                // search, then collapse the expanded row, then close.
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                    cursor = 0;
                    scroll = 0;
                    return true;
                }
                if (expanded != null) {
                    toggleExpanded(expanded);
                    return true;
                }
            }
            default -> {
                // Fall through to the superclass for anything unhandled.
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        // No focus to manage: the search field owns typing whenever the palette is open.
        searchQuery += chr;
        cursor = 0;
        scroll = 0;
        return true;
    }

    @Override
    public void close() {
        SettingRenderer.closePopups();
        SettingRenderer.releaseDrag();
        ConfigManager.save();
        super.close();
    }
}
