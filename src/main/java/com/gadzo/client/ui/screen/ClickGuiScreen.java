package com.gadzo.client.ui.screen;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.Setting;
import com.gadzo.client.core.system.SystemProfile;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The mods menu: category sidebar, searchable module list, and a settings panel.
 *
 * <p>Laid out as a single centred window rather than a full-screen takeover so the world
 * stays visible behind the blur, which is what makes tweaking a HUD element while in-game
 * practical.
 */
public class ClickGuiScreen extends Screen {

    private static final double WINDOW_WIDTH = 620;
    private static final double WINDOW_HEIGHT = 380;
    private static final double SIDEBAR_WIDTH = 132;
    private static final double HEADER_HEIGHT = 46;
    private static final double FOOTER_HEIGHT = 26;
    private static final double MODULE_ROW_HEIGHT = 34;
    private static final double PADDING = 10;

    /** Fractional split between the module list and the settings panel. */
    private static final double LIST_FRACTION = 0.48;

    private final Animation openAnimation = new Animation(0.0, 300L, Easing.EXPO_OUT);
    private final Map<ModuleCategory, Animation> categoryHover = new EnumMap<>(ModuleCategory.class);
    private final Map<Module, Animation> moduleHover = new java.util.HashMap<>();
    private final Map<Module, Animation> moduleToggle = new java.util.HashMap<>();

    private ModuleCategory selectedCategory = ModuleCategory.PERFORMANCE;
    private Module selectedModule;

    private String searchQuery = "";
    private boolean searchFocused;

    private double listScroll;
    private double settingsScroll;

    /** Cached each frame so input handlers agree with what was drawn. */
    private double windowX;
    private double windowY;

    public ClickGuiScreen() {
        super(Text.literal("GADZO"));
        for (ModuleCategory category : ModuleCategory.values()) {
            categoryHover.put(category, new Animation(0.0, 160L));
        }
    }

    @Override
    protected void init() {
        openAnimation.to(1.0);
        windowX = (width - WINDOW_WIDTH) / 2.0;
        windowY = (height - WINDOW_HEIGHT) / 2.0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // -- layout helpers -------------------------------------------------------------------

    private double contentX() {
        return windowX + SIDEBAR_WIDTH;
    }

    private double contentWidth() {
        return WINDOW_WIDTH - SIDEBAR_WIDTH;
    }

    private double listWidth() {
        return contentWidth() * LIST_FRACTION;
    }

    private double listTop() {
        return windowY + HEADER_HEIGHT;
    }

    private double listBottom() {
        return windowY + WINDOW_HEIGHT - FOOTER_HEIGHT;
    }

    private double settingsX() {
        return contentX() + listWidth();
    }

    private double settingsWidth() {
        return contentWidth() - listWidth();
    }

    /**
     * Y of the first setting row in the settings panel.
     *
     * <p>Shared by the render pass and the click handler; if these two disagree by even a few
     * pixels, clicks land on the wrong control.
     */
    private double settingsFirstRowY() {
        // Header block: title line, description line, separator, then the row area.
        return listTop() + 8 - settingsScroll + (textRenderer.fontHeight + 2) + (textRenderer.fontHeight + 8) + 8;
    }

    /** Modules shown in the list for the current category and search query. */
    private List<Module> visibleModules() {
        if (!searchQuery.isBlank()) {
            return GadzoClient.modules().search(searchQuery);
        }
        return GadzoClient.modules().byCategory(selectedCategory);
    }

    private Animation hoverOf(Module module) {
        return moduleHover.computeIfAbsent(module, ignored -> new Animation(0.0, 150L));
    }

    private Animation toggleOf(Module module) {
        return moduleToggle.computeIfAbsent(module,
                m -> new Animation(m.isEnabled() ? 1.0 : 0.0, 200L));
    }

    // -- rendering --------------------------------------------------------------------------

    /**
     * Frosted backdrop.
     *
     * <p>The dim is chosen by whether the blur actually ran. Blur already separates the panel
     * from the world, so stacking the full dim on top of it would hide the very effect it is
     * there to reveal; without blur, the dim is the only thing doing that job and has to carry
     * it alone.
     */
    private void drawBackdrop(DrawContext gfx) {
        double open = openAnimation.value();
        boolean blurred = Theme.blurEnabled() && Render2D.blurBehind(gfx);
        Render2D.rect(gfx, 0, 0, width, height,
                ColorUtil.fade(blurred ? 0x38000000 : 0xB0000000, open));
    }

    @Override
    public void render(DrawContext gfx, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(gfx);
        windowX = (width - WINDOW_WIDTH) / 2.0;
        windowY = (height - WINDOW_HEIGHT) / 2.0;

        double open = openAnimation.value();

        // Slide the window up slightly as it fades in.
        double slide = (1.0 - open) * 18.0;
        gfx.getMatrices().push();
        gfx.getMatrices().translate(0.0f, (float) slide, 0.0f);

        drawWindow(gfx, mouseX, mouseY, open);

        gfx.getMatrices().pop();
    }

    private void drawWindow(DrawContext gfx, int mouseX, int mouseY, double alpha) {
        Render2D.shadow(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(), 10,
                ColorUtil.fade(Theme.shadowColor(), alpha));
        Render2D.roundedRect(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(),
                ColorUtil.fade(Theme.background(), alpha));

        drawSidebar(gfx, mouseX, mouseY, alpha);
        drawHeader(gfx, mouseX, mouseY, alpha);
        drawModuleList(gfx, mouseX, mouseY, alpha);
        drawSettingsPanel(gfx, mouseX, mouseY, alpha);
        drawFooter(gfx, alpha);

        // The one boundary the whole window gets: a hairline catching light on the edge of
        // the glass. Everything inside is drawn without its own outline — this is the only
        // stroke in the panel, which is the point.
        Render2D.roundedOutline(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(),
                1.0, ColorUtil.fade(Theme.glassEdge(), alpha));
    }

    private void drawSidebar(DrawContext gfx, int mouseX, int mouseY, double alpha) {
        Render2D.roundedRect(gfx, windowX, windowY, SIDEBAR_WIDTH, WINDOW_HEIGHT,
                Theme.radius(), 0, 0, Theme.radius(),
                ColorUtil.fade(Theme.surface(), alpha));

        // Wordmark.
        Render2D.text(gfx, textRenderer, "GADZO", windowX + PADDING + 2, windowY + 16,
                ColorUtil.fade(Theme.accent(), alpha));
        Render2D.text(gfx, textRenderer, "CLIENT", windowX + PADDING + 2 + textRenderer.getWidth("GADZO") + 4,
                windowY + 16, ColorUtil.fade(Theme.textMuted(), alpha));

        double y = windowY + HEADER_HEIGHT;
        for (ModuleCategory category : ModuleCategory.values()) {
            boolean hovered = MathUtil.within(mouseX, mouseY, windowX + 6, y,
                    windowX + SIDEBAR_WIDTH - 6, y + 28);
            boolean selected = category == selectedCategory && searchQuery.isBlank();

            Animation animation = categoryHover.get(category);
            animation.toBoolean(hovered || selected);

            if (animation.value() > 0.01) {
                Render2D.roundedRect(gfx, windowX + 6, y, SIDEBAR_WIDTH - 12, 28, Theme.radiusSmall(),
                        ColorUtil.fade(selected
                                ? ColorUtil.withAlpha(Theme.accent(), 42)
                                : Theme.surfaceHover(), animation.value() * alpha));
            }
            if (selected) {
                // Accent pill on the leading edge marks the active category.
                Render2D.roundedRect(gfx, windowX + 6, y + 7, 3, 14, 1.5,
                        ColorUtil.fade(Theme.accent(), alpha));
            }

            Render2D.text(gfx, textRenderer, category.displayName(), windowX + 18, y + (28 - textRenderer.fontHeight) / 2.0,
                    ColorUtil.fade(selected ? Theme.textPrimary() : Theme.textSecondary(), alpha));

            long enabled = GadzoClient.modules().byCategory(category).stream()
                    .filter(Module::isEnabled).count();
            if (enabled > 0) {
                Render2D.textRight(gfx, textRenderer, Long.toString(enabled), windowX + SIDEBAR_WIDTH - 14,
                        y + (28 - textRenderer.fontHeight) / 2.0,
                        ColorUtil.fade(Theme.accent(), alpha), false);
            }
            y += 30;
        }
    }

    private void drawHeader(DrawContext gfx, int mouseX, int mouseY, double alpha) {
        double x = contentX() + PADDING;
        double y = windowY + 13;
        double boxWidth = contentWidth() - PADDING * 2;

        // No box, no outline at rest — the field is just a slightly lighter patch of the
        // panel's own glass. Focus is shown with a single thin accent underline rather than a
        // full border, the same pattern a modern web search field uses.
        Render2D.roundedRect(gfx, x, y, boxWidth, 20, Theme.radiusSmall(),
                ColorUtil.fade(Theme.surfaceHigh(), alpha * 0.7));
        if (searchFocused) {
            Render2D.rect(gfx, x + 6, y + 19, boxWidth - 12, 1.2,
                    ColorUtil.fade(Theme.accent(), alpha));
        }

        String display = searchQuery.isEmpty() && !searchFocused
                ? "Search modules..."
                : searchQuery + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        int textColor = searchQuery.isEmpty() && !searchFocused ? Theme.textMuted() : Theme.textPrimary();
        Render2D.text(gfx, textRenderer, display, x + 7, y + 6, ColorUtil.fade(textColor, alpha));
    }

    private void drawModuleList(DrawContext gfx, int mouseX, int mouseY, double alpha) {
        List<Module> modules = visibleModules();
        double x = contentX() + PADDING;
        double width = listWidth() - PADDING * 1.5;
        double top = listTop() + 6;
        double bottom = listBottom();

        clampListScroll(modules.size(), bottom - top);

        Render2D.pushScissor(gfx, contentX(), top, listWidth(), bottom - top);
        double y = top - listScroll;

        for (Module module : modules) {
            if (y + MODULE_ROW_HEIGHT >= top && y <= bottom) {
                drawModuleRow(gfx, module, x, y, width, mouseX, mouseY, alpha);
            }
            y += MODULE_ROW_HEIGHT + 4;
        }

        if (modules.isEmpty()) {
            Render2D.textCentered(gfx, textRenderer, "No modules match that search",
                    contentX() + listWidth() / 2.0, top + 20,
                    ColorUtil.fade(Theme.textMuted(), alpha), false);
        }
        Render2D.popScissor(gfx);
    }

    private void drawModuleRow(DrawContext gfx, Module module, double x, double y, double width,
                               int mouseX, int mouseY, double alpha) {
        boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + width, y + MODULE_ROW_HEIGHT);
        boolean selected = module == selectedModule;

        Animation hover = hoverOf(module);
        hover.toBoolean(hovered || selected);
        Animation toggle = toggleOf(module);
        toggle.toBoolean(module.isEnabled());

        // Rows sit directly on the panel's own glass at rest — no card behind every one of
        // them — and only pick up a soft tint on hover or selection. A background box on
        // every row regardless of state is exactly the boxy look a flat list should avoid.
        if (hover.value() > 0.01) {
            int tint = selected ? ColorUtil.withAlpha(Theme.accent(), 30) : Theme.surfaceHover();
            Render2D.roundedRect(gfx, x, y, width, MODULE_ROW_HEIGHT, Theme.radiusSmall(),
                    ColorUtil.fade(tint, hover.value() * alpha));
        }

        // Enabled state reads as a filled accent bar on the leading edge — the only boundary
        // this row draws, and it doubles as the selection marker.
        double barAlpha = Math.max(toggle.value(), selected ? 0.5 : 0.0);
        if (barAlpha > 0.01) {
            Render2D.roundedRect(gfx, x, y + 6, 3, MODULE_ROW_HEIGHT - 12, 1.5,
                    ColorUtil.fade(Theme.accent(), barAlpha * alpha));
        }

        double textX = x + 11;
        double nameWidth = width - 52;
        Render2D.text(gfx, textRenderer, Render2D.truncate(textRenderer, module.getName(), (int) nameWidth),
                textX, y + 6, ColorUtil.fade(Theme.textPrimary(), alpha));
        Render2D.text(gfx, textRenderer, Render2D.truncate(textRenderer, module.getDescription(), (int) nameWidth),
                textX, y + 18, ColorUtil.fade(Theme.textMuted(), alpha));

        if (module.isPermanent()) {
            Render2D.textRight(gfx, textRenderer, "always on", x + width - 10, y + 12,
                    ColorUtil.fade(Theme.textMuted(), alpha), false);
        } else {
            drawSmallToggle(gfx, x + width - 36, y + (MODULE_ROW_HEIGHT - TOGGLE_HEIGHT) / 2.0,
                    toggle.value(), alpha);
        }
    }

    private static final double TOGGLE_WIDTH = 30;
    private static final double TOGGLE_HEIGHT = 16;

    /**
     * The pill-and-knob switch used everywhere in this client.
     *
     * <p>The knob is a rounded square rather than a bare circle, sized a couple of pixels
     * larger than the tightest fit it could get away with. A perfect circle at a 5px radius
     * has only five scanlines of vertical resolution to describe a curve with, and no amount
     * of edge anti-aliasing makes that read as smooth — it needs more pixels to work with,
     * not a cleverer edge. This one gets them.
     */
    private void drawSmallToggle(DrawContext gfx, double x, double y, double t, double alpha) {
        int track = ColorUtil.mix(Theme.trackOff(), Theme.accent(), t);
        Render2D.roundedRect(gfx, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, TOGGLE_HEIGHT / 2.0,
                ColorUtil.fade(track, alpha));

        double knobSize = TOGGLE_HEIGHT - 4;
        double travel = TOGGLE_WIDTH - TOGGLE_HEIGHT;
        double knobX = x + 2 + travel * t;
        Render2D.roundedRect(gfx, knobX, y + 2, knobSize, knobSize, knobSize / 2.0,
                ColorUtil.fade(0xFFF4F6FA, alpha));
    }

    private void drawSettingsPanel(DrawContext gfx, int mouseX, int mouseY, double alpha) {
        double x = settingsX();
        double width = settingsWidth();
        double top = listTop();
        double bottom = listBottom();

        Render2D.rect(gfx, x, top, 1, bottom - top, ColorUtil.fade(Theme.border(), alpha));

        if (selectedModule == null) {
            Render2D.textCentered(gfx, textRenderer, "Select a module", x + width / 2.0,
                    top + (bottom - top) / 2.0 - 4, ColorUtil.fade(Theme.textMuted(), alpha), false);
            return;
        }

        double innerX = x + PADDING;
        double innerWidth = width - PADDING * 2;

        Render2D.pushScissor(gfx, x + 1, top, width - 1, bottom - top);
        double y = top + 8 - settingsScroll;

        Render2D.text(gfx, textRenderer, Render2D.truncate(textRenderer, selectedModule.getName(), (int) innerWidth),
                innerX, y, ColorUtil.fade(Theme.textPrimary(), alpha));
        y += textRenderer.fontHeight + 2;
        Render2D.text(gfx, textRenderer, Render2D.truncate(textRenderer, selectedModule.getDescription(), (int) innerWidth),
                innerX, y, ColorUtil.fade(Theme.textMuted(), alpha));
        y += textRenderer.fontHeight + 8;

        Render2D.separator(gfx, innerX, y, innerWidth, ColorUtil.fade(Theme.border(), alpha));
        y = settingsFirstRowY();

        // Keybind row first: it is the one control every module has.
        SettingRenderer.render(gfx, textRenderer, selectedModule.getKeybind(), innerX, y, innerWidth, mouseX, mouseY);
        y += SettingRenderer.ROW_HEIGHT;

        for (Setting<?> setting : selectedModule.getVisibleSettings()) {
            SettingRenderer.render(gfx, textRenderer, setting, innerX, y, innerWidth, mouseX, mouseY);
            y += SettingRenderer.ROW_HEIGHT + SettingRenderer.extraHeight(setting, textRenderer);
        }

        Render2D.popScissor(gfx);
    }

    private void drawFooter(DrawContext gfx, double alpha) {
        double y = windowY + WINDOW_HEIGHT - FOOTER_HEIGHT;
        Render2D.separator(gfx, contentX(), y, contentWidth(), ColorUtil.fade(Theme.border(), alpha));

        // Under Performance, show what the machine is doing rather than the profile name —
        // that is the context a player needs while changing these settings.
        String left = selectedCategory == ModuleCategory.PERFORMANCE && searchQuery.isBlank()
                ? SystemProfile.detectTier() + " tier  ·  " + SystemProfile.cpuThreads() + " threads"
                : GadzoClient.modules().enabledCount() + " enabled  ·  profile: "
                        + ConfigManager.activeProfile();

        Render2D.text(gfx, textRenderer, Render2D.truncate(textRenderer, left, (int) (contentWidth() - 90)),
                contentX() + PADDING, y + 9, ColorUtil.fade(Theme.textMuted(), alpha));
        Render2D.textRight(gfx, textRenderer, "v" + GadzoClient.VERSION,
                windowX + WINDOW_WIDTH - PADDING, y + 9,
                ColorUtil.fade(Theme.textMuted(), alpha), false);
    }

    private void clampListScroll(int count, double viewportHeight) {
        double contentHeight = count * (MODULE_ROW_HEIGHT + 4);
        double max = Math.max(0, contentHeight - viewportHeight);
        listScroll = MathUtil.clamp(listScroll, 0, max);
    }

    // -- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double clickX, double clickY, int button) {
        double mouseX = clickX;
        double mouseY = clickY;

        // Search box.
        double searchX = contentX() + PADDING;
        double searchY = windowY + 13;
        double searchWidth = contentWidth() - PADDING * 2;
        searchFocused = MathUtil.within(mouseX, mouseY, searchX, searchY,
                searchX + searchWidth, searchY + 20);
        if (searchFocused) {
            return true;
        }

        if (handleSettingsClick(mouseX, mouseY, button)) {
            return true;
        }
        // A click outside an open popup closes it rather than falling through.
        if (SettingRenderer.openDropdown() != null || SettingRenderer.openPicker() != null) {
            SettingRenderer.closePopups();
            return true;
        }
        if (handleSidebarClick(mouseX, mouseY)) {
            return true;
        }
        if (handleModuleListClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(clickX, clickY, button);
    }

    private boolean handleSidebarClick(double mouseX, double mouseY) {
        double y = windowY + HEADER_HEIGHT;
        for (ModuleCategory category : ModuleCategory.values()) {
            if (MathUtil.within(mouseX, mouseY, windowX + 6, y, windowX + SIDEBAR_WIDTH - 6, y + 28)) {
                selectedCategory = category;
                searchQuery = "";
                listScroll = 0;
                return true;
            }
            y += 30;
        }
        return false;
    }

    private boolean handleModuleListClick(double mouseX, double mouseY, int button) {
        if (!MathUtil.within(mouseX, mouseY, contentX(), listTop(), contentX() + listWidth(), listBottom())) {
            return false;
        }
        double x = contentX() + PADDING;
        double width = listWidth() - PADDING * 1.5;
        double y = listTop() + 6 - listScroll;

        for (Module module : visibleModules()) {
            if (MathUtil.within(mouseX, mouseY, x, y, x + width, y + MODULE_ROW_HEIGHT)) {
                // Right side of the row toggles; the rest selects it for editing.
                boolean onToggle = mouseX >= x + width - 40 && !module.isPermanent();
                if (onToggle || button == 1) {
                    module.toggle();
                } else {
                    selectedModule = module;
                    settingsScroll = 0;
                }
                return true;
            }
            y += MODULE_ROW_HEIGHT + 4;
        }
        return true;
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
        if (selectedModule == null) {
            return false;
        }
        double innerX = settingsX() + PADDING;
        double innerWidth = settingsWidth() - PADDING * 2;
        double y = settingsFirstRowY();

        if (SettingRenderer.mouseClicked(selectedModule.getKeybind(), textRenderer, innerX, y, innerWidth,
                mouseX, mouseY, button)) {
            return true;
        }
        y += SettingRenderer.ROW_HEIGHT;

        for (Setting<?> setting : selectedModule.getVisibleSettings()) {
            if (SettingRenderer.mouseClicked(setting, textRenderer, innerX, y, innerWidth, mouseX, mouseY, button)) {
                return true;
            }
            y += SettingRenderer.ROW_HEIGHT + SettingRenderer.extraHeight(setting, textRenderer);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double clickX, double clickY, int button, double deltaX, double deltaY) {
        if (SettingRenderer.isDragging()) {
            SettingRenderer.mouseDragged(settingsX() + PADDING, settingsWidth() - PADDING * 2,
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
        double amount = vertical * 18;
        if (mouseX >= settingsX()) {
            settingsScroll = Math.max(0, settingsScroll - amount);
        } else {
            listScroll = Math.max(0, listScroll - amount);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // A listening keybind row swallows every key, including escape, so it can be cleared.
        if (selectedModule != null) {
            if (SettingRenderer.keyPressed(selectedModule.getKeybind(), keyCode)) {
                return true;
            }
            for (Setting<?> setting : selectedModule.getVisibleSettings()) {
                if (SettingRenderer.keyPressed(setting, keyCode)) {
                    return true;
                }
            }
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                } else {
                    searchFocused = false;
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused) {
            searchQuery += String.valueOf(chr);
            listScroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        SettingRenderer.closePopups();
        SettingRenderer.releaseDrag();
        ConfigManager.save();
        super.close();
    }
}
