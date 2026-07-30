package com.gadzo.client.ui.screen;

import com.gadzo.client.integration.create.CreateKnowledge;
import com.gadzo.client.integration.create.StressCalculator;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Reference and stress planner for the Create mod.
 *
 * <p>Create does not exist for Minecraft 26.2, so nothing here reads a live kinetic network —
 * it is a planning tool. The last tab is the part that earns its place: a stress calculator,
 * which is otherwise a job people do in a spreadsheet.
 */
public class CreateHelperScreen extends Screen {

    private static final double WINDOW_WIDTH = 620;
    private static final double WINDOW_HEIGHT = 380;
    private static final double SIDEBAR_WIDTH = 140;
    private static final double HEADER_HEIGHT = 46;
    private static final double FOOTER_HEIGHT = 24;
    private static final double PADDING = 10;

    /** Index used by the sidebar for the calculator, which is not a knowledge topic. */
    private static final int CALCULATOR_TAB = -2;

    private final StressCalculator calculator = new StressCalculator();

    private int selectedTopic;
    private String searchQuery = "";
    private boolean searchFocused;
    private double scroll;

    private double windowX;
    private double windowY;

    public CreateHelperScreen() {
        super(Component.literal("Create helper"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double contentX() {
        return windowX + SIDEBAR_WIDTH;
    }

    private double contentWidth() {
        return WINDOW_WIDTH - SIDEBAR_WIDTH;
    }

    private double bodyTop() {
        return windowY + HEADER_HEIGHT;
    }

    private double bodyBottom() {
        return windowY + WINDOW_HEIGHT - FOOTER_HEIGHT;
    }

    // -- rendering ------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (Theme.blurEnabled()) {
            Render2D.blurBehind(gfx);
        }
        Render2D.rect(gfx, 0, 0, width, height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        windowX = (width - WINDOW_WIDTH) / 2.0;
        windowY = (height - WINDOW_HEIGHT) / 2.0;

        Render2D.shadow(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(), 10,
                Theme.shadowColor());
        Render2D.roundedRect(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(),
                Theme.background());

        drawSidebar(gfx, mouseX, mouseY);
        drawHeader(gfx);

        if (selectedTopic == CALCULATOR_TAB) {
            drawCalculator(gfx, mouseX, mouseY);
        } else {
            drawEntries(gfx);
        }
        drawFooter(gfx);
    }

    private void drawSidebar(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        Render2D.roundedRect(gfx, windowX, windowY, SIDEBAR_WIDTH, WINDOW_HEIGHT,
                Theme.radius(), 0, 0, Theme.radius(), Theme.surface());

        Render2D.text(gfx, font, "CREATE", windowX + PADDING + 2, windowY + 16, Theme.accent());
        Render2D.text(gfx, font, "HELPER", windowX + PADDING + 2 + font.width("CREATE") + 4,
                windowY + 16, Theme.textMuted());

        double y = windowY + HEADER_HEIGHT;
        List<CreateKnowledge.Topic> topics = CreateKnowledge.topics();

        for (int i = 0; i < topics.size(); i++) {
            drawSidebarItem(gfx, topics.get(i).name(), i, y, mouseX, mouseY);
            y += 28;
        }
        y += 6;
        drawSidebarItem(gfx, "Stress calculator", CALCULATOR_TAB, y, mouseX, mouseY);
    }

    private void drawSidebarItem(GuiGraphicsExtractor gfx, String label, int index, double y,
                                 int mouseX, int mouseY) {
        boolean selected = selectedTopic == index;
        boolean hovered = MathUtil.within(mouseX, mouseY, windowX + 6, y,
                windowX + SIDEBAR_WIDTH - 6, y + 26);

        if (selected || hovered) {
            Render2D.roundedRect(gfx, windowX + 6, y, SIDEBAR_WIDTH - 12, 26, Theme.radiusSmall(),
                    selected ? ColorUtil.withAlpha(Theme.accent(), 42) : Theme.surfaceHover());
        }
        if (selected) {
            Render2D.roundedRect(gfx, windowX + 6, y + 6, 3, 14, 1.5, Theme.accent());
        }
        Render2D.text(gfx, font, Render2D.truncate(font, label, (int) (SIDEBAR_WIDTH - 30)),
                windowX + 18, y + (26 - font.lineHeight) / 2.0,
                selected ? Theme.textPrimary() : Theme.textSecondary());
    }

    private void drawHeader(GuiGraphicsExtractor gfx) {
        double x = contentX() + PADDING;
        double y = windowY + 13;
        double boxWidth = contentWidth() - PADDING * 2;

        Render2D.roundedRect(gfx, x, y, boxWidth, 20, Theme.radiusSmall(), Theme.surface());
        Render2D.roundedOutline(gfx, x, y, boxWidth, 20, Theme.radiusSmall(), 1.0,
                searchFocused ? Theme.accent() : Theme.border());

        String display = searchQuery.isEmpty() && !searchFocused
                ? "Search the reference..."
                : searchQuery + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        Render2D.text(gfx, font, display, x + 7, y + 6,
                searchQuery.isEmpty() && !searchFocused ? Theme.textMuted() : Theme.textPrimary());

        Render2D.separator(gfx, contentX(), windowY + HEADER_HEIGHT - 1, contentWidth(), Theme.border());
    }

    private void drawEntries(GuiGraphicsExtractor gfx) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;

        List<CreateKnowledge.Entry> entries = searchQuery.isBlank()
                ? CreateKnowledge.topics().get(selectedTopic).entries()
                : CreateKnowledge.searchAll(searchQuery);

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());
        double y = bodyTop() + 8 - scroll;

        if (entries.isEmpty()) {
            Render2D.text(gfx, font, "Nothing matches that search.", x, y, Theme.textMuted());
        }

        for (CreateKnowledge.Entry entry : entries) {
            Render2D.text(gfx, font, entry.title(), x, y, Theme.accent());
            y += font.lineHeight + 4;

            for (String paragraph : entry.body()) {
                // Wrap by hand: the vanilla wrapper works on Components and this is plain text.
                for (String wrapped : wrap(paragraph, (int) innerWidth)) {
                    Render2D.text(gfx, font, wrapped, x, y, Theme.textSecondary());
                    y += font.lineHeight + 1;
                }
                y += 3;
            }
            y += 8;
        }

        Render2D.popScissor(gfx);
        contentHeightCache = y + scroll - (bodyTop() + 8);
    }

    /** Height of the last rendered content, used to clamp scrolling. */
    private double contentHeightCache;

    /** Greedy word wrap to a pixel width. */
    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void drawCalculator(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;
        double listWidth = innerWidth * 0.52;

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());
        double y = bodyTop() + 6 - scroll;

        Render2D.text(gfx, font, "Click to add, right-click to remove", x, y, Theme.textMuted());
        y += font.lineHeight + 6;

        for (StressCalculator.Machine machine : StressCalculator.MACHINES) {
            boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + listWidth, y + 15);
            if (hovered) {
                Render2D.roundedRect(gfx, x - 3, y - 2, listWidth + 6, 15, Theme.radiusSmall(),
                        Theme.surfaceHover());
            }
            int count = countOf(machine);
            Render2D.text(gfx, font, Render2D.truncate(font, machine.name(), (int) (listWidth - 54)),
                    x, y, count > 0 ? Theme.textPrimary() : Theme.textSecondary());
            Render2D.textRight(gfx, font, machine.impact() + " /rpm", x + listWidth - 22, y,
                    Theme.textMuted(), false);
            if (count > 0) {
                Render2D.textRight(gfx, font, "x" + count, x + listWidth, y, Theme.accent(), false);
            }
            y += 15;
        }

        // Summary column.
        double summaryX = x + listWidth + 14;
        double summaryY = bodyTop() + 6 - scroll;
        double summaryWidth = innerWidth - listWidth - 14;

        double impact = calculator.totalImpact();
        Render2D.text(gfx, font, "Plan", summaryX, summaryY, Theme.accent());
        summaryY += font.lineHeight + 4;
        Render2D.text(gfx, font, calculator.totalMachines() + " machines", summaryX, summaryY,
                Theme.textSecondary());
        summaryY += font.lineHeight + 1;
        Render2D.text(gfx, font, String.format("%.0f su/rpm", impact), summaryX, summaryY,
                Theme.textPrimary());
        summaryY += font.lineHeight + 1;
        Render2D.text(gfx, font, String.format("%.0f SU at 64 rpm", calculator.stressAtSpeed(64)),
                summaryX, summaryY, Theme.textMuted());
        summaryY += font.lineHeight + 8;

        Render2D.text(gfx, font, "Generators needed", summaryX, summaryY, Theme.accent());
        summaryY += font.lineHeight + 4;

        for (StressCalculator.Generator generator : StressCalculator.GENERATORS) {
            int needed = calculator.generatorsNeeded(generator);
            String label = Render2D.truncate(font, generator.name(), (int) (summaryWidth - 26));
            Render2D.text(gfx, font, label, summaryX, summaryY, Theme.textSecondary());
            Render2D.textRight(gfx, font, needed == 0 ? "-" : "x" + needed,
                    summaryX + summaryWidth, summaryY,
                    needed == 0 ? Theme.textMuted() : Theme.textPrimary(), false);
            summaryY += font.lineHeight + 1;
        }

        summaryY += 6;
        for (String wrapped : wrap("Speed does not buy headroom: impact and capacity both scale "
                + "with RPM. Verify against goggles in game.", (int) summaryWidth)) {
            Render2D.text(gfx, font, wrapped, summaryX, summaryY, Theme.textMuted());
            summaryY += font.lineHeight;
        }

        Render2D.popScissor(gfx);
        contentHeightCache = Math.max(y, summaryY) + scroll - bodyTop();
    }

    private int countOf(StressCalculator.Machine machine) {
        for (StressCalculator.Line line : calculator.lines()) {
            if (line.machine().equals(machine)) {
                return line.count();
            }
        }
        return 0;
    }

    private void drawFooter(GuiGraphicsExtractor gfx) {
        double y = windowY + WINDOW_HEIGHT - FOOTER_HEIGHT;
        Render2D.separator(gfx, contentX(), y, contentWidth(), Theme.border());

        boolean createLoaded = FabricLoader.getInstance().isModLoaded("create");
        String status = createLoaded
                ? "Create detected"
                : "Reference only — Create has no build for Minecraft 26.2";
        Render2D.text(gfx, font, status, contentX() + PADDING, y + 8,
                createLoaded ? Theme.success() : Theme.textMuted());
        Render2D.textRight(gfx, font, CreateKnowledge.totalEntries() + " entries",
                windowX + WINDOW_WIDTH - PADDING, y + 8, Theme.textMuted(), false);
    }

    // -- input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        double searchX = contentX() + PADDING;
        double searchY = windowY + 13;
        searchFocused = MathUtil.within(mouseX, mouseY, searchX, searchY,
                searchX + contentWidth() - PADDING * 2, searchY + 20);
        if (searchFocused) {
            return true;
        }

        double y = windowY + HEADER_HEIGHT;
        List<CreateKnowledge.Topic> topics = CreateKnowledge.topics();
        for (int i = 0; i < topics.size(); i++) {
            if (MathUtil.within(mouseX, mouseY, windowX + 6, y, windowX + SIDEBAR_WIDTH - 6, y + 26)) {
                selectedTopic = i;
                scroll = 0;
                return true;
            }
            y += 28;
        }
        y += 6;
        if (MathUtil.within(mouseX, mouseY, windowX + 6, y, windowX + SIDEBAR_WIDTH - 6, y + 26)) {
            selectedTopic = CALCULATOR_TAB;
            scroll = 0;
            return true;
        }

        if (selectedTopic == CALCULATOR_TAB) {
            return handleCalculatorClick(mouseX, mouseY, event.button());
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleCalculatorClick(double mouseX, double mouseY, int button) {
        double x = contentX() + PADDING;
        double listWidth = (contentWidth() - PADDING * 2) * 0.52;
        double y = bodyTop() + 6 - scroll + font.lineHeight + 6;

        for (StressCalculator.Machine machine : StressCalculator.MACHINES) {
            if (MathUtil.within(mouseX, mouseY, x, y, x + listWidth, y + 15)) {
                if (button == 1) {
                    calculator.remove(machine);
                } else {
                    calculator.add(machine);
                }
                return true;
            }
            y += 15;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double viewport = bodyBottom() - bodyTop();
        double max = Math.max(0, contentHeightCache - viewport + 16);
        scroll = MathUtil.clamp(scroll - vertical * 18, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
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
            scroll = 0;
            return true;
        }
        return super.charTyped(event);
    }
}
