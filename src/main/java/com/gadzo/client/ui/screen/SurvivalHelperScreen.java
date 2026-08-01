package com.gadzo.client.ui.screen;

import com.gadzo.client.survival.FoodTable;
import com.gadzo.client.survival.NetherCalculator;
import com.gadzo.client.survival.SurvivalKnowledge;
import com.gadzo.client.survival.Waypoint;
import com.gadzo.client.survival.WaypointStore;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;
import com.gadzo.client.util.Mc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference and tools for vanilla survival.
 *
 * <p>Four tabs, three of them live: the waypoint list is the real store and edits it, the Nether
 * calculator reads the position you are standing in, and the food table is built from the item
 * registry so it includes whatever mods are installed.
 *
 * <p>The reference used to be one sidebar row per topic. It is now a single scrolling document
 * with an always-focused filter, which is both less chrome and less work — with a couple of
 * dozen entries, typing three letters beats picking a topic and then reading down it, and the
 * search can cross topic boundaries, which the sidebar could not.
 */
public class SurvivalHelperScreen extends GlassScreen {

    private static final int TAB_REFERENCE = 0;
    private static final int TAB_WAYPOINTS = 1;
    private static final int TAB_NETHER = 2;
    private static final int TAB_FOOD = 3;

    private static final double WAYPOINT_ROW = 18;
    private static final double FOOD_ROW = 13;

    /** Waypoint-row hover, keyed by row index. */
    private final Map<Integer, Animation> waypointHover = new HashMap<>();

    public SurvivalHelperScreen() {
        super("Survival");
    }

    @Override
    protected List<String> tabs() {
        return List.of("Reference", "Waypoints", "Nether", "Food");
    }

    @Override
    protected boolean searchable() {
        return activeTab == TAB_REFERENCE;
    }

    @Override
    protected String searchPlaceholder() {
        return "Filter " + SurvivalKnowledge.totalEntries() + " entries";
    }

    @Override
    protected String headerRight() {
        return switch (activeTab) {
            case TAB_WAYPOINTS -> WaypointStore.allInWorld().size() + " in this world";
            case TAB_FOOD -> FoodTable.all().size() + " edible items";
            default -> null;
        };
    }

    @Override
    protected String hint() {
        return switch (activeTab) {
            case TAB_WAYPOINTS -> "click show or hide    right-click delete";
            case TAB_NETHER -> "Y is never divided by 8";
            case TAB_FOOD -> "ranked by saturation — what decides how long you stay fed";
            default -> "type to filter    tab next    esc close";
        };
    }

    private Animation waypointHoverOf(int index) {
        return waypointHover.computeIfAbsent(index, ignored -> new Animation(0.0, 120L));
    }

    // -- body -----------------------------------------------------------------------------

    @Override
    protected void drawBody(DrawContext gfx, int mouseX, int mouseY) {
        switch (activeTab) {
            case TAB_WAYPOINTS -> drawWaypoints(gfx, mouseX, mouseY);
            case TAB_NETHER -> drawNether(gfx);
            case TAB_FOOD -> drawFood(gfx);
            default -> drawReference(gfx);
        }
    }

    /**
     * The whole reference as one document.
     *
     * <p>Topic headings are kept while browsing so the material still has a shape, and dropped
     * while filtering — once a query is typed, grouping results under headings that mostly hold
     * one line each is noise around the answer.
     */
    private void drawReference(DrawContext gfx) {
        double x = contentX();
        double innerWidth = contentWidth();
        double y = bodyOrigin();

        if (!searchQuery.isBlank()) {
            List<SurvivalKnowledge.Entry> matches = SurvivalKnowledge.searchAll(searchQuery);
            if (matches.isEmpty()) {
                body(gfx, "Nothing matches that.", x, y);
                contentHeight = 40;
                return;
            }
            for (SurvivalKnowledge.Entry entry : matches) {
                y = drawEntry(gfx, entry, x, y, innerWidth);
            }
            contentHeight = y + scroll - bodyTop();
            return;
        }

        for (SurvivalKnowledge.Topic topic : SurvivalKnowledge.topics()) {
            sectionLabel(gfx, topic.name(), x, y);
            y += textRenderer.fontHeight + 7;
            for (SurvivalKnowledge.Entry entry : topic.entries()) {
                y = drawEntry(gfx, entry, x, y, innerWidth);
            }
            y += 8;
        }
        contentHeight = y + scroll - bodyTop();
    }

    private double drawEntry(DrawContext gfx, SurvivalKnowledge.Entry entry, double x, double y,
                             double innerWidth) {
        Render2D.text(gfx, textRenderer, entry.title(), x, y,
                ColorUtil.fade(Theme.textPrimary(), openAlpha));
        y += textRenderer.fontHeight + 4;

        for (String paragraph : entry.body()) {
            for (String wrapped : wrap(paragraph, (int) innerWidth)) {
                body(gfx, wrapped, x, y);
                y += textRenderer.fontHeight + 1;
            }
            y += 3;
        }
        return y + 8;
    }

    // -- waypoints ------------------------------------------------------------------------

    private double waypointRowY(int index) {
        return bodyOrigin() + index * WAYPOINT_ROW;
    }

    private void drawWaypoints(DrawContext gfx, int mouseX, int mouseY) {
        double x = contentX();
        double innerWidth = contentWidth();

        List<Waypoint> waypoints = WaypointStore.allInWorld();
        if (waypoints.isEmpty()) {
            body(gfx, "No waypoints in this world yet. Press the Waypoints keybind to drop one.",
                    x, bodyOrigin());
            contentHeight = 40;
            return;
        }

        ClientPlayerEntity player = Mc.player();
        String dimension = WaypointStore.currentDimension();
        double y = bodyOrigin();

        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            y = waypointRowY(i);

            boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + innerWidth, y + WAYPOINT_ROW);
            Animation hover = waypointHoverOf(i);
            hover.toBoolean(hovered);
            if (hover.value() > 0.01) {
                Render2D.roundedRect(gfx, x - 6, y - 1, innerWidth + 12, WAYPOINT_ROW,
                        Theme.radiusSmall(),
                        ColorUtil.fade(Theme.surfaceHover(), hover.value() * openAlpha));
            }

            // The colour swatch doubles as the visibility indicator: faded means hidden.
            int swatch = waypoint.visible() ? waypoint.color()
                    : ColorUtil.fade(waypoint.color(), 0.3);
            Render2D.circle(gfx, x + 4, y + WAYPOINT_ROW / 2.0 - 1, 3.4,
                    ColorUtil.fade(swatch, openAlpha));

            double textY = y + (WAYPOINT_ROW - textRenderer.fontHeight) / 2.0 - 1;
            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, waypoint.name(), 130), x + 14, textY,
                    ColorUtil.fade(waypoint.visible() ? Theme.textPrimary() : Theme.textMuted(),
                            openAlpha));

            Render2D.text(gfx, textRenderer,
                    waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z(),
                    x + 152, textY, ColorUtil.fade(Theme.textSecondary(), openAlpha));

            boolean here = waypoint.dimension().equals(dimension);
            String right;
            if (!here) {
                right = shortDimension(waypoint.dimension());
            } else if (player != null) {
                right = Math.round(waypoint.horizontalDistanceTo(player.getPos())) + " m";
            } else {
                right = "";
            }
            Render2D.textRight(gfx, textRenderer, right, x + innerWidth, textY,
                    ColorUtil.fade(here ? Theme.textMuted() : Theme.warning(), openAlpha), false);
        }

        contentHeight = y + WAYPOINT_ROW + scroll - bodyTop();
    }

    /** Trims {@code minecraft:the_nether} down to something that fits in a column. */
    private String shortDimension(String dimension) {
        int colon = dimension.indexOf(':');
        String path = colon >= 0 ? dimension.substring(colon + 1) : dimension;
        return path.replace("the_", "").replace('_', ' ');
    }

    // -- nether calculator ----------------------------------------------------------------

    private void drawNether(DrawContext gfx) {
        double x = contentX();
        double innerWidth = contentWidth();
        double y = bodyOrigin();

        ClientPlayerEntity player = Mc.player();
        String dimension = WaypointStore.currentDimension();

        if (player == null || dimension == null) {
            body(gfx, "Join a world to convert your position.", x, y);
            contentHeight = 40;
            return;
        }

        boolean inNether = dimension.equals("minecraft:the_nether");
        int px = (int) Math.floor(player.getX());
        int pz = (int) Math.floor(player.getZ());

        sectionLabel(gfx, "Where you are standing", x, y);
        y += textRenderer.fontHeight + 7;

        keyValue(gfx, inNether ? "Nether" : "Overworld", px + ", " + pz, x, x + innerWidth, y);
        y += textRenderer.fontHeight + 4;

        int cx = inNether ? NetherCalculator.toOverworld(px) : NetherCalculator.toNether(px);
        int cz = inNether ? NetherCalculator.toOverworld(pz) : NetherCalculator.toNether(pz);

        Render2D.text(gfx, textRenderer, inNether ? "Overworld" : "Nether", x, y,
                ColorUtil.fade(Theme.textSecondary(), openAlpha));
        Render2D.textRight(gfx, textRenderer, cx + ", " + cz, x + innerWidth, y,
                ColorUtil.fade(Theme.accent(), openAlpha), false);
        y += textRenderer.fontHeight + 10;

        for (String wrapped : wrap(NetherCalculator.describe(px, pz, inNether), (int) innerWidth)) {
            body(gfx, wrapped, x, y);
            y += textRenderer.fontHeight + 1;
        }
        y += 14;

        sectionLabel(gfx, "Waypoints, converted", x, y);
        y += textRenderer.fontHeight + 7;

        List<Waypoint> waypoints = WaypointStore.allInWorld();
        if (waypoints.isEmpty()) {
            body(gfx, "No waypoints to convert.", x, y);
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
                    Render2D.truncate(textRenderer, waypoint.name(), 120), x, y,
                    ColorUtil.fade(Theme.textSecondary(), openAlpha));
            Render2D.textRight(gfx, textRenderer,
                    waypoint.x() + ", " + waypoint.z() + "   →   " + wx + ", " + wz,
                    x + innerWidth, y, ColorUtil.fade(Theme.textPrimary(), openAlpha), false);
            y += textRenderer.fontHeight + 3;
        }

        contentHeight = y + scroll - bodyTop();
    }

    // -- food table -----------------------------------------------------------------------

    private void drawFood(DrawContext gfx) {
        double x = contentX();
        double innerWidth = contentWidth();
        double y = bodyOrigin();

        double hungerRight = x + innerWidth - 116;
        double saturationRight = x + innerWidth - 46;
        double ratioRight = x + innerWidth;

        int muted = ColorUtil.fade(Theme.textMuted(), openAlpha);
        Render2D.text(gfx, textRenderer, "Food", x, y, muted);
        Render2D.textRight(gfx, textRenderer, "hunger", hungerRight, y, muted, false);
        Render2D.textRight(gfx, textRenderer, "saturation", saturationRight, y, muted, false);
        Render2D.textRight(gfx, textRenderer, "ratio", ratioRight, y, muted, false);
        y += textRenderer.fontHeight + 7;

        for (FoodTable.Food food : FoodTable.all()) {
            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, food.name(), (int) (innerWidth - 170)), x, y,
                    ColorUtil.fade(Theme.textSecondary(), openAlpha));
            Render2D.textRight(gfx, textRenderer, Integer.toString(food.hunger()),
                    hungerRight, y, muted, false);
            Render2D.textRight(gfx, textRenderer, String.format("%.1f", food.saturation()),
                    saturationRight, y, ColorUtil.fade(Theme.textPrimary(), openAlpha), false);
            Render2D.textRight(gfx, textRenderer, String.format("%.1f", food.ratio()),
                    ratioRight, y, muted, false);
            y += FOOD_ROW;
        }

        contentHeight = y + scroll - bodyTop();
    }

    // -- input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeTab == TAB_WAYPOINTS && handleWaypointClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleWaypointClick(double mouseX, double mouseY, int button) {
        double x = contentX();
        double innerWidth = contentWidth();
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
}
