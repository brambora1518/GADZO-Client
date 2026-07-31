package com.gadzo.client.ui.screen;

import com.gadzo.client.survival.FoodTable;
import com.gadzo.client.survival.NetherCalculator;
import com.gadzo.client.survival.SurvivalKnowledge;
import com.gadzo.client.survival.Waypoint;
import com.gadzo.client.survival.WaypointStore;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference and tools for vanilla survival.
 *
 * <p>Three of the four tabs are live rather than static: the waypoint list is the real store and
 * edits it, the Nether calculator reads the position you are standing in, and the food table is
 * built from the item registry so it includes whatever mods are installed. Only the reference
 * text is fixed.
 */
public class SurvivalHelperScreen extends Screen {

    private static final double WINDOW_WIDTH = 640;
    private static final double WINDOW_HEIGHT = 400;
    private static final double SIDEBAR_WIDTH = 146;
    private static final double HEADER_HEIGHT = 46;
    private static final double FOOTER_HEIGHT = 24;
    private static final double PADDING = 10;
    private static final double SIDEBAR_ROW = 24;
    private static final double SIDEBAR_GROUP_GAP = 8;

    private static final int WAYPOINTS_TAB = -2;
    private static final int NETHER_TAB = -3;
    private static final int FOOD_TAB = -4;

    private static final double WAYPOINT_ROW = 16;
    private static final double FOOD_ROW = 12;

    private int selectedTopic;
    private String searchQuery = "";
    private boolean searchFocused;
    private double scroll;
    private double contentHeightCache;

    private double windowX;
    private double windowY;

    public SurvivalHelperScreen() {
        super(Text.literal("Survival helper"));
    }

    @Override
    public boolean shouldPause() {
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

    // -- sidebar --------------------------------------------------------------------------

    private record SidebarItem(String label, int index, boolean startsGroup) {
    }

    private List<SidebarItem> sidebarItems() {
        List<SidebarItem> items = new ArrayList<>();
        List<SurvivalKnowledge.Topic> topics = SurvivalKnowledge.topics();
        for (int i = 0; i < topics.size(); i++) {
            items.add(new SidebarItem(topics.get(i).name(), i, false));
        }
        items.add(new SidebarItem("Waypoints", WAYPOINTS_TAB, true));
        items.add(new SidebarItem("Nether calculator", NETHER_TAB, false));
        items.add(new SidebarItem("Food table", FOOD_TAB, false));
        return items;
    }

    private double sidebarItemY(int row) {
        List<SidebarItem> items = sidebarItems();
        double y = windowY + HEADER_HEIGHT - 4;
        for (int i = 0; i <= row && i < items.size(); i++) {
            if (items.get(i).startsGroup()) {
                y += SIDEBAR_GROUP_GAP;
            }
            if (i < row) {
                y += SIDEBAR_ROW;
            }
        }
        return y;
    }

    // -- rendering ------------------------------------------------------------------------

    @Override
    public void render(DrawContext gfx, int mouseX, int mouseY, float partialTick) {
        if (Theme.blurEnabled()) {
            Render2D.blurBehind(gfx);
        }
        Render2D.rect(gfx, 0, 0, width, height, 0xB0000000);

        windowX = (width - WINDOW_WIDTH) / 2.0;
        windowY = (height - WINDOW_HEIGHT) / 2.0;

        Render2D.shadow(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(), 10,
                Theme.shadowColor());
        Render2D.roundedRect(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(),
                Theme.background());

        drawSidebar(gfx, mouseX, mouseY);
        drawHeader(gfx);

        switch (selectedTopic) {
            case WAYPOINTS_TAB -> drawWaypoints(gfx, mouseX, mouseY);
            case NETHER_TAB -> drawNether(gfx);
            case FOOD_TAB -> drawFood(gfx);
            default -> drawEntries(gfx);
        }
        drawFooter(gfx);
    }

    private void drawSidebar(DrawContext gfx, int mouseX, int mouseY) {
        Render2D.roundedRect(gfx, windowX, windowY, SIDEBAR_WIDTH, WINDOW_HEIGHT,
                Theme.radius(), 0, 0, Theme.radius(), Theme.surface());

        Render2D.text(gfx, textRenderer, "SURVIVAL", windowX + PADDING + 2, windowY + 16,
                Theme.accent());
        Render2D.text(gfx, textRenderer, "HELPER",
                windowX + PADDING + 2 + textRenderer.getWidth("SURVIVAL") + 4,
                windowY + 16, Theme.textMuted());

        List<SidebarItem> items = sidebarItems();
        for (int i = 0; i < items.size(); i++) {
            SidebarItem item = items.get(i);
            double y = sidebarItemY(i);
            boolean selected = selectedTopic == item.index();
            boolean hovered = MathUtil.within(mouseX, mouseY, windowX + 6, y,
                    windowX + SIDEBAR_WIDTH - 6, y + SIDEBAR_ROW);

            if (selected || hovered) {
                Render2D.roundedRect(gfx, windowX + 6, y, SIDEBAR_WIDTH - 12, SIDEBAR_ROW,
                        Theme.radiusSmall(),
                        selected ? ColorUtil.withAlpha(Theme.accent(), 42) : Theme.surfaceHover());
            }
            if (selected) {
                Render2D.roundedRect(gfx, windowX + 6, y + 5, 3, SIDEBAR_ROW - 10, 1.5,
                        Theme.accent());
            }
            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, item.label(), (int) (SIDEBAR_WIDTH - 30)),
                    windowX + 18, y + (SIDEBAR_ROW - textRenderer.fontHeight) / 2.0,
                    selected ? Theme.textPrimary() : Theme.textSecondary());
        }
    }

    private void drawHeader(DrawContext gfx) {
        double x = contentX() + PADDING;
        double y = windowY + 13;
        double boxWidth = contentWidth() - PADDING * 2;

        Render2D.roundedRect(gfx, x, y, boxWidth, 20, Theme.radiusSmall(), Theme.surface());
        Render2D.roundedOutline(gfx, x, y, boxWidth, 20, Theme.radiusSmall(), 1.0,
                searchFocused ? Theme.accent() : Theme.border());

        String display = searchQuery.isEmpty() && !searchFocused
                ? "Search the reference..."
                : searchQuery + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0
                        ? "_" : "");
        Render2D.text(gfx, textRenderer, display, x + 7, y + 6,
                searchQuery.isEmpty() && !searchFocused ? Theme.textMuted() : Theme.textPrimary());

        Render2D.separator(gfx, contentX(), windowY + HEADER_HEIGHT - 1, contentWidth(),
                Theme.border());
    }

    private void drawEntries(DrawContext gfx) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;

        List<SurvivalKnowledge.Entry> entries = searchQuery.isBlank()
                ? SurvivalKnowledge.topics().get(selectedTopic).entries()
                : SurvivalKnowledge.searchAll(searchQuery);

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());
        double y = bodyTop() + 8 - scroll;

        if (entries.isEmpty()) {
            Render2D.text(gfx, textRenderer, "Nothing matches that search.", x, y,
                    Theme.textMuted());
        }

        for (SurvivalKnowledge.Entry entry : entries) {
            Render2D.text(gfx, textRenderer, entry.title(), x, y, Theme.accent());
            y += textRenderer.fontHeight + 4;

            for (String paragraph : entry.body()) {
                for (String wrapped : wrap(paragraph, (int) innerWidth)) {
                    Render2D.text(gfx, textRenderer, wrapped, x, y, Theme.textSecondary());
                    y += textRenderer.fontHeight + 1;
                }
                y += 3;
            }
            y += 8;
        }

        Render2D.popScissor(gfx);
        contentHeightCache = y + scroll - (bodyTop() + 8);
    }

    // -- waypoints ------------------------------------------------------------------------

    private double waypointRowY(int index) {
        return bodyTop() + 8 - scroll + textRenderer.fontHeight + 8 + index * WAYPOINT_ROW;
    }

    private void drawWaypoints(DrawContext gfx, int mouseX, int mouseY) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());

        Render2D.text(gfx, textRenderer, "Click to show or hide, right-click to delete", x,
                bodyTop() + 8 - scroll, Theme.textMuted());

        List<Waypoint> waypoints = WaypointStore.allInWorld();
        double y = bodyTop();

        if (waypoints.isEmpty()) {
            Render2D.text(gfx, textRenderer,
                    "No waypoints in this world yet. Press the Waypoints keybind to drop one.",
                    x, waypointRowY(0), Theme.textSecondary());
            Render2D.popScissor(gfx);
            contentHeightCache = 40;
            return;
        }

        ClientPlayerEntity player = Mc.player();
        String dimension = WaypointStore.currentDimension();

        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            y = waypointRowY(i);

            boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + innerWidth, y + WAYPOINT_ROW);
            if (hovered) {
                Render2D.roundedRect(gfx, x - 3, y - 1, innerWidth + 6, WAYPOINT_ROW,
                        Theme.radiusSmall(), Theme.surfaceHover());
            }

            // The colour swatch doubles as the visibility indicator: faded means hidden.
            int swatch = waypoint.visible() ? waypoint.color()
                    : ColorUtil.fade(waypoint.color(), 0.3);
            Render2D.roundedRect(gfx, x, y + 3, 6, 6, 3, swatch);

            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, waypoint.name(), 90), x + 12, y + 2,
                    waypoint.visible() ? Theme.textPrimary() : Theme.textMuted());

            Render2D.text(gfx, textRenderer,
                    waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z(),
                    x + 110, y + 2, Theme.textSecondary());

            boolean here = waypoint.dimension().equals(dimension);
            String right;
            if (!here) {
                right = shortDimension(waypoint.dimension());
            } else if (player != null) {
                right = Math.round(waypoint.horizontalDistanceTo(player.getPos())) + " m";
            } else {
                right = "";
            }
            Render2D.textRight(gfx, textRenderer, right, x + innerWidth, y + 2,
                    here ? Theme.textMuted() : Theme.warning(), false);
        }

        Render2D.popScissor(gfx);
        contentHeightCache = y + WAYPOINT_ROW + scroll - bodyTop();
    }

    /** Trims {@code minecraft:the_nether} down to something that fits in a column. */
    private String shortDimension(String dimension) {
        int colon = dimension.indexOf(':');
        String path = colon >= 0 ? dimension.substring(colon + 1) : dimension;
        return path.replace("the_", "").replace('_', ' ');
    }

    // -- nether calculator ----------------------------------------------------------------

    private void drawNether(DrawContext gfx) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());
        double y = bodyTop() + 8 - scroll;

        ClientPlayerEntity player = Mc.player();
        String dimension = WaypointStore.currentDimension();

        if (player == null || dimension == null) {
            Render2D.text(gfx, textRenderer, "Join a world to convert your position.", x, y,
                    Theme.textMuted());
            Render2D.popScissor(gfx);
            contentHeightCache = 40;
            return;
        }

        boolean inNether = dimension.equals("minecraft:the_nether");
        int px = (int) Math.floor(player.getX());
        int pz = (int) Math.floor(player.getZ());

        Render2D.text(gfx, textRenderer, "Where you are standing", x, y, Theme.accent());
        y += textRenderer.fontHeight + 5;

        Render2D.text(gfx, textRenderer,
                String.format("%s   %d, %d", inNether ? "Nether" : "Overworld", px, pz),
                x, y, Theme.textPrimary());
        y += textRenderer.fontHeight + 3;

        int cx = inNether ? NetherCalculator.toOverworld(px) : NetherCalculator.toNether(px);
        int cz = inNether ? NetherCalculator.toOverworld(pz) : NetherCalculator.toNether(pz);

        Render2D.text(gfx, textRenderer,
                String.format("%s   %d, %d", inNether ? "Overworld" : "Nether", cx, cz),
                x, y, Theme.accent());
        y += textRenderer.fontHeight + 8;

        for (String wrapped : wrap(NetherCalculator.describe(px, pz, inNether), (int) innerWidth)) {
            Render2D.text(gfx, textRenderer, wrapped, x, y, Theme.textSecondary());
            y += textRenderer.fontHeight + 1;
        }
        y += 10;

        Render2D.separator(gfx, x, y, innerWidth, Theme.border());
        y += 8;

        Render2D.text(gfx, textRenderer, "Waypoints, converted", x, y, Theme.accent());
        y += textRenderer.fontHeight + 5;

        List<Waypoint> waypoints = WaypointStore.allInWorld();
        if (waypoints.isEmpty()) {
            Render2D.text(gfx, textRenderer, "No waypoints to convert.", x, y, Theme.textMuted());
            y += textRenderer.fontHeight;
        }
        for (Waypoint waypoint : waypoints) {
            boolean waypointInNether = waypoint.dimension().equals("minecraft:the_nether");
            int wx = waypointInNether
                    ? NetherCalculator.toOverworld(waypoint.x())
                    : NetherCalculator.toNether(waypoint.x());
            int wz = waypointInNether
                    ? NetherCalculator.toOverworld(waypoint.z())
                    : NetherCalculator.toNether(waypoint.z());

            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, waypoint.name(), 90), x, y,
                    Theme.textSecondary());
            Render2D.text(gfx, textRenderer,
                    String.format("%d, %d  →  %d, %d", waypoint.x(), waypoint.z(), wx, wz),
                    x + 110, y, Theme.textPrimary());
            y += textRenderer.fontHeight + 2;
        }

        Render2D.popScissor(gfx);
        contentHeightCache = y + scroll - bodyTop();
    }

    // -- food table -----------------------------------------------------------------------

    private void drawFood(DrawContext gfx) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());
        double y = bodyTop() + 8 - scroll;

        Render2D.text(gfx, textRenderer, "Ranked by saturation — the value that decides how long "
                + "you stay fed", x, y, Theme.textMuted());
        y += textRenderer.fontHeight + 6;

        Render2D.text(gfx, textRenderer, "Food", x, y, Theme.accent());
        Render2D.textRight(gfx, textRenderer, "hunger", x + innerWidth - 96, y, Theme.accent(),
                false);
        Render2D.textRight(gfx, textRenderer, "saturation", x + innerWidth - 32, y, Theme.accent(),
                false);
        Render2D.textRight(gfx, textRenderer, "ratio", x + innerWidth, y, Theme.accent(), false);
        y += textRenderer.fontHeight + 4;

        for (FoodTable.Food food : FoodTable.all()) {
            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, food.name(), (int) (innerWidth - 150)), x, y,
                    Theme.textSecondary());
            Render2D.textRight(gfx, textRenderer, Integer.toString(food.hunger()),
                    x + innerWidth - 96, y, Theme.textMuted(), false);
            Render2D.textRight(gfx, textRenderer, String.format("%.1f", food.saturation()),
                    x + innerWidth - 32, y, Theme.textPrimary(), false);
            Render2D.textRight(gfx, textRenderer, String.format("%.1f", food.ratio()),
                    x + innerWidth, y, Theme.textMuted(), false);
            y += FOOD_ROW;
        }

        Render2D.popScissor(gfx);
        contentHeightCache = y + scroll - bodyTop();
    }

    // -- footer ---------------------------------------------------------------------------

    private void drawFooter(DrawContext gfx) {
        double y = windowY + WINDOW_HEIGHT - FOOTER_HEIGHT;
        Render2D.separator(gfx, contentX(), y, contentWidth(), Theme.border());

        String left = switch (selectedTopic) {
            case WAYPOINTS_TAB -> WaypointStore.allInWorld().size() + " waypoints in this world";
            case FOOD_TAB -> FoodTable.all().size() + " edible items found";
            case NETHER_TAB -> "Y is never divided by 8";
            default -> "Written against Minecraft 1.20.1";
        };
        Render2D.text(gfx, textRenderer, left, contentX() + PADDING, y + 8, Theme.textMuted());
        Render2D.textRight(gfx, textRenderer, SurvivalKnowledge.totalEntries() + " entries",
                windowX + WINDOW_WIDTH - PADDING, y + 8, Theme.textMuted(), false);
    }

    /** Greedy word wrap to a pixel width. */
    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textRenderer.getWidth(candidate) > maxWidth && !current.isEmpty()) {
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

    // -- input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double searchX = contentX() + PADDING;
        double searchY = windowY + 13;
        searchFocused = MathUtil.within(mouseX, mouseY, searchX, searchY,
                searchX + contentWidth() - PADDING * 2, searchY + 20);
        if (searchFocused) {
            return true;
        }

        List<SidebarItem> items = sidebarItems();
        for (int i = 0; i < items.size(); i++) {
            double y = sidebarItemY(i);
            if (MathUtil.within(mouseX, mouseY, windowX + 6, y,
                    windowX + SIDEBAR_WIDTH - 6, y + SIDEBAR_ROW)) {
                selectedTopic = items.get(i).index();
                scroll = 0;
                return true;
            }
        }

        if (selectedTopic == WAYPOINTS_TAB) {
            return handleWaypointClick(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleWaypointClick(double mouseX, double mouseY, int button) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;
        List<Waypoint> waypoints = WaypointStore.allInWorld();

        for (int i = 0; i < waypoints.size(); i++) {
            double y = waypointRowY(i);
            if (!MathUtil.within(mouseX, mouseY, x, y, x + innerWidth, y + WAYPOINT_ROW)) {
                continue;
            }
            Waypoint waypoint = waypoints.get(i);
            if (button == 1) {
                WaypointStore.remove(waypoint.name());
            } else {
                WaypointStore.replace(waypoint.withVisible(!waypoint.visible()));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        double viewport = bodyBottom() - bodyTop();
        double max = Math.max(0, contentHeightCache - viewport + 16);
        scroll = MathUtil.clamp(scroll - vertical * 18, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
            scroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
}
