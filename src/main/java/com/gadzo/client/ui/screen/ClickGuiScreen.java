package com.gadzo.client.ui.screen;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.Setting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.Easing;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

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
        super(Component.literal("GADZO"));
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
    public boolean isPauseScreen() {
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
        return listTop() + 8 - settingsScroll + (font.lineHeight + 2) + (font.lineHeight + 8) + 8;
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        windowX = (width - WINDOW_WIDTH) / 2.0;
        windowY = (height - WINDOW_HEIGHT) / 2.0;

        double open = openAnimation.value();
        if (Theme.blurEnabled()) {
            Render2D.blurBehind(gfx);
        }
        // Dim the world so the window reads as a focused surface.
        Render2D.rect(gfx, 0, 0, width, height, ColorUtil.fade(0xB0000000, open));

        // Slide the window up slightly as it fades in.
        double slide = (1.0 - open) * 18.0;
        gfx.pose().pushMatrix();
        gfx.pose().translate(0.0f, (float) slide);

        drawWindow(gfx, mouseX, mouseY, open);

        gfx.pose().popMatrix();
    }

    private void drawWindow(GuiGraphicsExtractor gfx, int mouseX, int mouseY, double alpha) {
        Render2D.shadow(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(), 10,
                ColorUtil.fade(Theme.shadowColor(), alpha));
        Render2D.roundedRect(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(),
                ColorUtil.fade(Theme.background(), alpha));

        drawSidebar(gfx, mouseX, mouseY, alpha);
        drawHeader(gfx, mouseX, mouseY, alpha);
        drawModuleList(gfx, mouseX, mouseY, alpha);
        drawSettingsPanel(gfx, mouseX, mouseY, alpha);
        drawFooter(gfx, alpha);
    }

    private void drawSidebar(GuiGraphicsExtractor gfx, int mouseX, int mouseY, double alpha) {
        Render2D.roundedRect(gfx, windowX, windowY, SIDEBAR_WIDTH, WINDOW_HEIGHT,
                Theme.radius(), 0, 0, Theme.radius(),
                ColorUtil.fade(Theme.surface(), alpha));

        // Wordmark.
        Render2D.text(gfx, font, "GADZO", windowX + PADDING + 2, windowY + 16,
                ColorUtil.fade(Theme.accent(), alpha));
        Render2D.text(gfx, font, "CLIENT", windowX + PADDING + 2 + font.width("GADZO") + 4,
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

            Render2D.text(gfx, font, category.displayName(), windowX + 18, y + (28 - font.lineHeight) / 2.0,
                    ColorUtil.fade(selected ? Theme.textPrimary() : Theme.textSecondary(), alpha));

            long enabled = GadzoClient.modules().byCategory(category).stream()
                    .filter(Module::isEnabled).count();
            if (enabled > 0) {
                Render2D.textRight(gfx, font, Long.toString(enabled), windowX + SIDEBAR_WIDTH - 14,
                        y + (28 - font.lineHeight) / 2.0,
                        ColorUtil.fade(Theme.accent(), alpha), false);
            }
            y += 30;
        }
    }

    private void drawHeader(GuiGraphicsExtractor gfx, int mouseX, int mouseY, double alpha) {
        double x = contentX() + PADDING;
        double y = windowY + 13;
        double boxWidth = contentWidth() - PADDING * 2;

        boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + boxWidth, y + 20);
        int border = searchFocused ? Theme.accent() : Theme.border();

        Render2D.roundedRect(gfx, x, y, boxWidth, 20, Theme.radiusSmall(),
                ColorUtil.fade(Theme.surface(), alpha));
        Render2D.roundedOutline(gfx, x, y, boxWidth, 20, Theme.radiusSmall(), 1.0,
                ColorUtil.fade(border, alpha * (hovered || searchFocused ? 1.0 : 0.6)));

        String display = searchQuery.isEmpty() && !searchFocused
                ? "Search modules..."
                : searchQuery + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        int textColor = searchQuery.isEmpty() && !searchFocused ? Theme.textMuted() : Theme.textPrimary();
        Render2D.text(gfx, font, display, x + 7, y + 6, ColorUtil.fade(textColor, alpha));

        Render2D.separator(gfx, contentX(), windowY + HEADER_HEIGHT - 1, contentWidth(),
                ColorUtil.fade(Theme.border(), alpha));
    }

    private void drawModuleList(GuiGraphicsExtractor gfx, int mouseX, int mouseY, double alpha) {
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
            Render2D.textCentered(gfx, font, "No modules match that search",
                    contentX() + listWidth() / 2.0, top + 20,
                    ColorUtil.fade(Theme.textMuted(), alpha), false);
        }
        Render2D.popScissor(gfx);
    }

    private void drawModuleRow(GuiGraphicsExtractor gfx, Module module, double x, double y, double width,
                               int mouseX, int mouseY, double alpha) {
        boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + width, y + MODULE_ROW_HEIGHT);
        boolean selected = module == selectedModule;

        Animation hover = hoverOf(module);
        hover.toBoolean(hovered);
        Animation toggle = toggleOf(module);
        toggle.toBoolean(module.isEnabled());

        int background = ColorUtil.mix(Theme.surface(), Theme.surfaceHover(), hover.value());
        Render2D.roundedRect(gfx, x, y, width, MODULE_ROW_HEIGHT, Theme.radiusSmall(),
                ColorUtil.fade(background, alpha));

        if (selected) {
            Render2D.roundedOutline(gfx, x, y, width, MODULE_ROW_HEIGHT, Theme.radiusSmall(), 1.0,
                    ColorUtil.fade(Theme.accent(), alpha));
        }

        // Enabled state reads as a filled accent bar on the leading edge.
        if (toggle.value() > 0.01) {
            Render2D.roundedRect(gfx, x, y + 6, 3, MODULE_ROW_HEIGHT - 12, 1.5,
                    ColorUtil.fade(Theme.accent(), toggle.value() * alpha));
        }

        double textX = x + 11;
        double nameWidth = width - 52;
        Render2D.text(gfx, font, Render2D.truncate(font, module.getName(), (int) nameWidth),
                textX, y + 6, ColorUtil.fade(Theme.textPrimary(), alpha));
        Render2D.text(gfx, font, Render2D.truncate(font, module.getDescription(), (int) nameWidth),
                textX, y + 18, ColorUtil.fade(Theme.textMuted(), alpha));

        if (module.isPermanent()) {
            Render2D.textRight(gfx, font, "always on", x + width - 10, y + 12,
                    ColorUtil.fade(Theme.textMuted(), alpha), false);
        } else {
            drawSmallToggle(gfx, x + width - 34, y + (MODULE_ROW_HEIGHT - 14) / 2.0, toggle.value(), alpha);
        }
    }

    private void drawSmallToggle(GuiGraphicsExtractor gfx, double x, double y, double t, double alpha) {
        double w = 26;
        double h = 14;
        int track = ColorUtil.mix(Theme.trackOff(), Theme.accent(), t);
        Render2D.roundedRect(gfx, x, y, w, h, h / 2.0, ColorUtil.fade(track, alpha));
        Render2D.circle(gfx, x + h / 2.0 + (w - h) * t, y + h / 2.0, h / 2.0 - 2,
                ColorUtil.fade(0xFFFFFFFF, alpha));
    }

    private void drawSettingsPanel(GuiGraphicsExtractor gfx, int mouseX, int mouseY, double alpha) {
        double x = settingsX();
        double width = settingsWidth();
        double top = listTop();
        double bottom = listBottom();

        Render2D.rect(gfx, x, top, 1, bottom - top, ColorUtil.fade(Theme.border(), alpha));

        if (selectedModule == null) {
            Render2D.textCentered(gfx, font, "Select a module", x + width / 2.0,
                    top + (bottom - top) / 2.0 - 4, ColorUtil.fade(Theme.textMuted(), alpha), false);
            return;
        }

        double innerX = x + PADDING;
        double innerWidth = width - PADDING * 2;

        Render2D.pushScissor(gfx, x + 1, top, width - 1, bottom - top);
        double y = top + 8 - settingsScroll;

        Render2D.text(gfx, font, Render2D.truncate(font, selectedModule.getName(), (int) innerWidth),
                innerX, y, ColorUtil.fade(Theme.textPrimary(), alpha));
        y += font.lineHeight + 2;
        Render2D.text(gfx, font, Render2D.truncate(font, selectedModule.getDescription(), (int) innerWidth),
                innerX, y, ColorUtil.fade(Theme.textMuted(), alpha));
        y += font.lineHeight + 8;

        Render2D.separator(gfx, innerX, y, innerWidth, ColorUtil.fade(Theme.border(), alpha));
        y = settingsFirstRowY();

        // Keybind row first: it is the one control every module has.
        SettingRenderer.render(gfx, font, selectedModule.getKeybind(), innerX, y, innerWidth, mouseX, mouseY);
        y += SettingRenderer.ROW_HEIGHT;

        for (Setting<?> setting : selectedModule.getVisibleSettings()) {
            SettingRenderer.render(gfx, font, setting, innerX, y, innerWidth, mouseX, mouseY);
            y += SettingRenderer.ROW_HEIGHT + SettingRenderer.extraHeight(setting, font);
        }

        Render2D.popScissor(gfx);
    }

    private void drawFooter(GuiGraphicsExtractor gfx, double alpha) {
        double y = windowY + WINDOW_HEIGHT - FOOTER_HEIGHT;
        Render2D.separator(gfx, contentX(), y, contentWidth(), ColorUtil.fade(Theme.border(), alpha));

        String left = GadzoClient.modules().enabledCount() + " enabled  ·  profile: "
                + ConfigManager.activeProfile();
        Render2D.text(gfx, font, left, contentX() + PADDING, y + 9,
                ColorUtil.fade(Theme.textMuted(), alpha));
        Render2D.textRight(gfx, font, "v" + GadzoClient.VERSION,
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Search box.
        double searchX = contentX() + PADDING;
        double searchY = windowY + 13;
        double searchWidth = contentWidth() - PADDING * 2;
        searchFocused = MathUtil.within(mouseX, mouseY, searchX, searchY,
                searchX + searchWidth, searchY + 20);
        if (searchFocused) {
            return true;
        }

        if (handleSettingsClick(mouseX, mouseY, event.button())) {
            return true;
        }
        // A click outside an open dropdown closes it rather than falling through.
        if (SettingRenderer.openDropdown() != null) {
            SettingRenderer.closeDropdown();
            return true;
        }
        if (handleSidebarClick(mouseX, mouseY)) {
            return true;
        }
        if (handleModuleListClick(mouseX, mouseY, event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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

        if (SettingRenderer.mouseClicked(selectedModule.getKeybind(), font, innerX, y, innerWidth,
                mouseX, mouseY, button)) {
            return true;
        }
        y += SettingRenderer.ROW_HEIGHT;

        for (Setting<?> setting : selectedModule.getVisibleSettings()) {
            if (SettingRenderer.mouseClicked(setting, font, innerX, y, innerWidth, mouseX, mouseY, button)) {
                return true;
            }
            y += SettingRenderer.ROW_HEIGHT + SettingRenderer.extraHeight(setting, font);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (SettingRenderer.isDragging()) {
            SettingRenderer.mouseDragged(settingsX() + PADDING, settingsWidth() - PADDING * 2, event.x());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        SettingRenderer.releaseDrag();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double amount = vertical * 18;
        if (mouseX >= settingsX()) {
            settingsScroll = Math.max(0, settingsScroll - amount);
        } else {
            listScroll = Math.max(0, listScroll - amount);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // A listening keybind row swallows every key, including escape, so it can be cleared.
        if (selectedModule != null) {
            if (SettingRenderer.keyPressed(selectedModule.getKeybind(), event.key())) {
                return true;
            }
            for (Setting<?> setting : selectedModule.getVisibleSettings()) {
                if (SettingRenderer.keyPressed(setting, event.key())) {
                    return true;
                }
            }
        }

        if (searchFocused) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                } else {
                    searchFocused = false;
                }
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchFocused) {
            searchQuery += event.codepointAsString();
            listScroll = 0;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        SettingRenderer.closeDropdown();
        SettingRenderer.releaseDrag();
        ConfigManager.save();
        super.onClose();
    }
}
