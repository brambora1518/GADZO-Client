package com.gadzo.client.ui.screen;

import com.gadzo.client.integration.create.CreateBridge;
import com.gadzo.client.integration.create.CreateKnowledge;
import com.gadzo.client.integration.create.GearTrain;
import com.gadzo.client.integration.create.NetworkScanner;
import com.gadzo.client.integration.create.StressCalculator;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference, stress planner and gear-ratio solver for the Create mod.
 *
 * <p>Three different kinds of thing live here. The reference is static text. The stress planner
 * reads its per-block figures out of Create's own registry when the mod is installed, so a pack
 * that retunes stress stays correct, and falls back to built-in defaults for planning without
 * it. The ratio solver is arithmetic and works either way.
 *
 * <p>Sidebar geometry is computed once, in {@link #sidebarItems()} and {@link #sidebarItemY(int)},
 * and shared by the renderer and the click handler. Laying it out twice is how a menu ends up
 * highlighting one row and selecting another.
 */
public class CreateHelperScreen extends Screen {

    private static final double WINDOW_WIDTH = 640;
    private static final double WINDOW_HEIGHT = 400;
    private static final double SIDEBAR_WIDTH = 146;
    private static final double HEADER_HEIGHT = 46;
    private static final double FOOTER_HEIGHT = 24;
    private static final double PADDING = 10;
    private static final double SIDEBAR_ROW = 24;

    /** Gap drawn above a sidebar item that starts a new group. */
    private static final double SIDEBAR_GROUP_GAP = 8;

    /** Sidebar indices for the tabs that are not knowledge topics. */
    private static final int CALCULATOR_TAB = -2;
    private static final int RATIO_TAB = -3;
    private static final int NETWORKS_TAB = -4;

    /** How often the networks tab re-scans while it is the open tab. */
    private static final long NETWORK_RESCAN_MILLIS = 2000;

    private static final double STEPPER_SIZE = 18;

    private final StressCalculator calculator = new StressCalculator();

    /** Entrance motion: fades and slides the whole panel up, matching the mods menu. */
    private final Animation openAnimation = new Animation(0.0, 300L);

    /** Sidebar row hover, keyed by tab index rather than by object identity. */
    private final Map<Integer, Animation> sidebarHover = new HashMap<>();

    /** Machine-row hover in the stress calculator, keyed by row index. */
    private final Map<Integer, Animation> machineHover = new HashMap<>();

    /** Stepper button hover, keyed by a caller-chosen id ("source-", "source+", ...). */
    private final Map<String, Animation> stepperHover = new HashMap<>();

    private int selectedTopic;
    private String searchQuery = "";
    private boolean searchFocused;
    private double scroll;

    /** Height of the last rendered content, used to clamp scrolling. */
    private double contentHeightCache;

    private double windowX;
    private double windowY;

    // Ratio solver state. Defaults describe the most common early-game question there is:
    // a water wheel at 8 RPM feeding machines that want to run faster.
    private int sourceRpm = 8;
    private int targetRpm = 32;
    private int windmillSails = 32;

    /** Y of the sails stepper, captured during render so clicks survive scrolling. */
    private double windmillRowY;

    // Networks tab state. The scan itself is real work — every loaded block entity in a
    // multi-chunk radius — so it runs on a timer rather than every frame; see NetworkScanner.
    private List<NetworkScanner.NetworkInfo> networks = List.of();
    private long networksScannedAt;

    public CreateHelperScreen() {
        super(Text.literal("Create helper"));
    }

    @Override
    protected void init() {
        openAnimation.to(1.0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Animation sidebarHoverOf(int index) {
        return sidebarHover.computeIfAbsent(index, ignored -> new Animation(0.0, 150L));
    }

    private Animation machineHoverOf(int index) {
        return machineHover.computeIfAbsent(index, ignored -> new Animation(0.0, 120L));
    }

    private Animation stepperHoverOf(String id) {
        return stepperHover.computeIfAbsent(id, ignored -> new Animation(0.0, 120L));
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

    // -- sidebar model --------------------------------------------------------------------

    /** One sidebar row: a label, the tab it selects, and whether it starts a new group. */
    private record SidebarItem(String label, int index, boolean startsGroup) {
    }

    private List<SidebarItem> sidebarItems() {
        List<SidebarItem> items = new ArrayList<>();
        List<CreateKnowledge.Topic> topics = CreateKnowledge.topics();
        for (int i = 0; i < topics.size(); i++) {
            items.add(new SidebarItem(topics.get(i).name(), i, false));
        }
        items.add(new SidebarItem("Stress calculator", CALCULATOR_TAB, true));
        items.add(new SidebarItem("Gear ratios", RATIO_TAB, false));
        items.add(new SidebarItem("Networks nearby", NETWORKS_TAB, false));
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

    private void drawBackdrop(DrawContext gfx, double open) {
        if (Theme.blurEnabled()) {
            Render2D.blurBehind(gfx);
        }
        Render2D.rect(gfx, 0, 0, width, height, ColorUtil.fade(0xB0000000, open));
    }

    @Override
    public void render(DrawContext gfx, int mouseX, int mouseY, float partialTick) {
        double open = openAnimation.value();
        drawBackdrop(gfx, open);
        windowX = (width - WINDOW_WIDTH) / 2.0;
        windowY = (height - WINDOW_HEIGHT) / 2.0;

        // Slides up from slightly below rest as it opens, matching the mods menu so every
        // GADZO panel enters the same way rather than three of the four popping in instantly.
        double slide = (1.0 - open) * 18.0;
        gfx.getMatrices().push();
        gfx.getMatrices().translate(0.0f, (float) slide, 0.0f);

        Render2D.shadow(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(), 10,
                Theme.shadowColor());
        Render2D.roundedRect(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(),
                Theme.background());

        drawSidebar(gfx, mouseX, mouseY);
        drawHeader(gfx);

        if (selectedTopic == CALCULATOR_TAB) {
            drawCalculator(gfx, mouseX, mouseY);
        } else if (selectedTopic == RATIO_TAB) {
            drawRatios(gfx, mouseX, mouseY);
        } else if (selectedTopic == NETWORKS_TAB) {
            drawNetworks(gfx);
        } else {
            drawEntries(gfx);
        }
        drawFooter(gfx);

        gfx.getMatrices().pop();
    }

    private void drawSidebar(DrawContext gfx, int mouseX, int mouseY) {
        Render2D.roundedRect(gfx, windowX, windowY, SIDEBAR_WIDTH, WINDOW_HEIGHT,
                Theme.radius(), 0, 0, Theme.radius(), Theme.surface());

        Render2D.text(gfx, textRenderer, "CREATE", windowX + PADDING + 2, windowY + 16,
                Theme.accent());
        Render2D.text(gfx, textRenderer, "HELPER",
                windowX + PADDING + 2 + textRenderer.getWidth("CREATE") + 4,
                windowY + 16, Theme.textMuted());

        List<SidebarItem> items = sidebarItems();
        for (int i = 0; i < items.size(); i++) {
            drawSidebarItem(gfx, items.get(i), sidebarItemY(i), mouseX, mouseY);
        }
    }

    private void drawSidebarItem(DrawContext gfx, SidebarItem item, double y, int mouseX,
                                 int mouseY) {
        boolean selected = selectedTopic == item.index();
        boolean hovered = MathUtil.within(mouseX, mouseY, windowX + 6, y,
                windowX + SIDEBAR_WIDTH - 6, y + SIDEBAR_ROW);

        Animation hover = sidebarHoverOf(item.index());
        hover.toBoolean(hovered || selected);

        if (hover.value() > 0.01) {
            int highlight = selected ? ColorUtil.withAlpha(Theme.accent(), 42) : Theme.surfaceHover();
            Render2D.roundedRect(gfx, windowX + 6, y, SIDEBAR_WIDTH - 12, SIDEBAR_ROW,
                    Theme.radiusSmall(), ColorUtil.fade(highlight, hover.value()));
        }
        if (selected) {
            Render2D.roundedRect(gfx, windowX + 6, y + 5, 3, SIDEBAR_ROW - 10, 1.5, Theme.accent());
        }
        Render2D.text(gfx, textRenderer,
                Render2D.truncate(textRenderer, item.label(), (int) (SIDEBAR_WIDTH - 30)),
                windowX + 18, y + (SIDEBAR_ROW - textRenderer.fontHeight) / 2.0,
                selected ? Theme.textPrimary() : Theme.textSecondary());
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

        List<CreateKnowledge.Entry> entries = searchQuery.isBlank()
                ? CreateKnowledge.topics().get(selectedTopic).entries()
                : CreateKnowledge.searchAll(searchQuery);

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());
        double y = bodyTop() + 8 - scroll;

        if (entries.isEmpty()) {
            Render2D.text(gfx, textRenderer, "Nothing matches that search.", x, y,
                    Theme.textMuted());
        }

        for (CreateKnowledge.Entry entry : entries) {
            Render2D.text(gfx, textRenderer, entry.title(), x, y, Theme.accent());
            y += textRenderer.fontHeight + 4;

            for (String paragraph : entry.body()) {
                // Wrap by hand: the vanilla wrapper works on Text objects and this is plain text.
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

    // -- stress calculator ----------------------------------------------------------------

    private double machineRowY(int index) {
        return bodyTop() + 6 - scroll + textRenderer.fontHeight + 6 + index * 15;
    }

    private void drawCalculator(DrawContext gfx, int mouseX, int mouseY) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;
        double listWidth = innerWidth * 0.52;

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());

        Render2D.text(gfx, textRenderer, "Click to add, right-click to remove", x,
                bodyTop() + 6 - scroll, Theme.textMuted());

        List<StressCalculator.Machine> machines = StressCalculator.MACHINES;
        double listBottom = bodyTop();

        for (int i = 0; i < machines.size(); i++) {
            StressCalculator.Machine machine = machines.get(i);
            double y = machineRowY(i);

            boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + listWidth, y + 15);
            Animation hover = machineHoverOf(i);
            hover.toBoolean(hovered);
            if (hover.value() > 0.01) {
                Render2D.roundedRect(gfx, x - 3, y - 2, listWidth + 6, 15, Theme.radiusSmall(),
                        ColorUtil.fade(Theme.surfaceHover(), hover.value()));
            }
            int count = countOf(machine);
            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, machine.name(), (int) (listWidth - 62)),
                    x, y, count > 0 ? Theme.textPrimary() : Theme.textSecondary());
            Render2D.textRight(gfx, textRenderer, String.format("%.0f /rpm", machine.impact()),
                    x + listWidth - 22, y, Theme.textMuted(), false);
            if (count > 0) {
                Render2D.textRight(gfx, textRenderer, "x" + count, x + listWidth, y,
                        Theme.accent(), false);
            }
            listBottom = y + 15;
        }

        // Summary column.
        double summaryX = x + listWidth + 14;
        double summaryY = bodyTop() + 6 - scroll;
        double summaryWidth = innerWidth - listWidth - 14;

        Render2D.text(gfx, textRenderer, "Plan", summaryX, summaryY, Theme.accent());
        summaryY += textRenderer.fontHeight + 4;
        Render2D.text(gfx, textRenderer, calculator.totalMachines() + " machines", summaryX,
                summaryY, Theme.textSecondary());
        summaryY += textRenderer.fontHeight + 1;
        Render2D.text(gfx, textRenderer, String.format("%.0f su/rpm", calculator.totalImpact()),
                summaryX, summaryY, Theme.textPrimary());
        summaryY += textRenderer.fontHeight + 1;
        Render2D.text(gfx, textRenderer,
                String.format("%.0f SU at 64 rpm", calculator.stressAtSpeed(64)),
                summaryX, summaryY, Theme.textMuted());
        summaryY += textRenderer.fontHeight + 8;

        Render2D.text(gfx, textRenderer, "Generators needed", summaryX, summaryY, Theme.accent());
        summaryY += textRenderer.fontHeight + 4;

        for (StressCalculator.Generator generator : StressCalculator.GENERATORS) {
            int needed = calculator.generatorsNeeded(generator);
            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, generator.name(), (int) (summaryWidth - 26)),
                    summaryX, summaryY, Theme.textSecondary());
            Render2D.textRight(gfx, textRenderer, needed == 0 ? "-" : "x" + needed,
                    summaryX + summaryWidth, summaryY,
                    needed == 0 ? Theme.textMuted() : Theme.textPrimary(), false);
            summaryY += textRenderer.fontHeight + 1;

            Render2D.text(gfx, textRenderer,
                    String.format("   %.0f SU at %d rpm", generator.totalStressUnits(),
                            generator.rpm()),
                    summaryX, summaryY, Theme.textMuted());
            summaryY += textRenderer.fontHeight + 3;
        }

        summaryY += 4;
        for (String wrapped : wrap("Gearing the whole network up changes nothing: impact and "
                + "capacity both scale with RPM. Gearing up only the machines does raise the "
                + "load.", (int) summaryWidth)) {
            Render2D.text(gfx, textRenderer, wrapped, summaryX, summaryY, Theme.textMuted());
            summaryY += textRenderer.fontHeight;
        }

        Render2D.popScissor(gfx);
        contentHeightCache = Math.max(listBottom, summaryY) + scroll - bodyTop();
    }

    private int countOf(StressCalculator.Machine machine) {
        for (StressCalculator.Line line : calculator.lines()) {
            if (line.machine().equals(machine)) {
                return line.count();
            }
        }
        return 0;
    }

    // -- gear ratios ----------------------------------------------------------------------

    /** Y of one of the two RPM stepper rows. */
    private double ratioRowY(int row) {
        return bodyTop() + 10 - scroll + row * 22;
    }

    private void drawRatios(DrawContext gfx, int mouseX, int mouseY) {
        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());

        drawStepper(gfx, "Source speed", sourceRpm + " RPM", ratioRowY(0), mouseX, mouseY);
        drawStepper(gfx, "Target speed", targetRpm + " RPM", ratioRowY(1), mouseX, mouseY);

        double y = ratioRowY(2) + 6;
        GearTrain.Plan plan = GearTrain.solve(sourceRpm, targetRpm);

        Render2D.text(gfx, textRenderer, "Result", x, y, Theme.accent());
        y += textRenderer.fontHeight + 4;

        Render2D.text(gfx, textRenderer,
                String.format("%d RPM — %s", (int) plan.resultRpm(),
                        plan.exact() ? "exact" : "closest reachable"),
                x, y, plan.exact() ? Theme.success() : Theme.warning());
        y += textRenderer.fontHeight + 4;

        if (plan.steps().isEmpty()) {
            Render2D.text(gfx, textRenderer, "No cogwheel pairs needed.", x, y,
                    Theme.textSecondary());
            y += textRenderer.fontHeight + 2;
        } else {
            int step = 1;
            for (GearTrain.Step gearStep : plan.steps()) {
                Render2D.text(gfx, textRenderer, step + ".  " + gearStep.description(), x, y,
                        Theme.textSecondary());
                y += textRenderer.fontHeight + 2;
                step++;
            }
            Render2D.text(gfx, textRenderer,
                    plan.reversesDirection()
                            ? "Output turns the opposite way to the source."
                            : "Output turns the same way as the source.",
                    x, y, Theme.textMuted());
            y += textRenderer.fontHeight + 2;
        }

        y += 4;
        for (String wrapped : wrap(plan.note(), (int) innerWidth)) {
            Render2D.text(gfx, textRenderer, wrapped, x, y,
                    plan.exact() ? Theme.textSecondary() : Theme.warning());
            y += textRenderer.fontHeight + 1;
        }

        y += 10;
        Render2D.separator(gfx, x, y, innerWidth, Theme.border());
        y += 8;

        Render2D.text(gfx, textRenderer, "Windmill", x, y, Theme.accent());
        y += textRenderer.fontHeight + 4;

        windmillRowY = y;
        drawStepper(gfx, "Sails", Integer.toString(windmillSails), y, mouseX, mouseY);
        y += 24;

        for (String wrapped : wrap(GearTrain.windmillAdvice(windmillSails), (int) innerWidth)) {
            Render2D.text(gfx, textRenderer, wrapped, x, y, Theme.textSecondary());
            y += textRenderer.fontHeight + 1;
        }

        Render2D.popScissor(gfx);
        contentHeightCache = y + scroll - bodyTop();
    }

    /**
     * A labelled value with a minus and a plus button.
     *
     * <p>Button rectangles come from {@link #stepperMinusX()} and {@link #stepperPlusX()} so the
     * click handler tests exactly the boxes that were drawn.
     */
    private void drawStepper(DrawContext gfx, String label, String value, double y,
                             int mouseX, int mouseY) {
        // The hover id has to be unique per stepper on screen, and rows repeat across tabs and
        // scroll positions — the label is stable and unique in practice, unlike y.
        drawStepper(gfx, label, value, y, mouseX, mouseY, label);
    }

    private void drawStepper(DrawContext gfx, String label, String value, double y,
                             int mouseX, int mouseY, String hoverId) {
        Render2D.text(gfx, textRenderer, label, contentX() + PADDING, y + 5, Theme.textSecondary());

        double minusX = stepperMinusX();
        double plusX = stepperPlusX();

        drawStepperButton(gfx, "-", minusX, y, mouseX, mouseY, hoverId + "-");
        drawStepperButton(gfx, "+", plusX, y, mouseX, mouseY, hoverId + "+");

        Render2D.textCentered(gfx, textRenderer, value,
                (minusX + STEPPER_SIZE + plusX) / 2.0, y + 5, Theme.textPrimary(), false);
        Render2D.text(gfx, textRenderer, "scroll to adjust", plusX + STEPPER_SIZE + 10, y + 5,
                Theme.textMuted());
    }

    private double stepperMinusX() {
        return contentX() + PADDING + 84;
    }

    private double stepperPlusX() {
        return contentX() + PADDING + 156;
    }

    private void drawStepperButton(DrawContext gfx, String glyph, double x, double y,
                                   int mouseX, int mouseY, String hoverId) {
        boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + STEPPER_SIZE, y + STEPPER_SIZE);
        Animation hover = stepperHoverOf(hoverId);
        hover.toBoolean(hovered);

        int background = ColorUtil.mix(Theme.surface(), Theme.surfaceHover(), hover.value());
        Render2D.roundedRect(gfx, x, y, STEPPER_SIZE, STEPPER_SIZE, Theme.radiusSmall(), background);
        Render2D.textCentered(gfx, textRenderer, glyph, x + STEPPER_SIZE / 2.0, y + 5,
                Theme.textPrimary(), false);
    }

    /**
     * Step size for an RPM stepper.
     *
     * <p>Scales with the value because the speeds that matter in Create are powers of two:
     * stepping by one from 128 would take sixty-four clicks to reach the next interesting one.
     */
    private int rpmStep(int current) {
        if (current <= 16) return 1;
        if (current <= 64) return 4;
        return 16;
    }

    private int adjustRpm(int current, int direction) {
        return MathUtil.clamp(current + rpmStep(current) * direction, 1, GearTrain.MAX_RPM);
    }

    // -- networks nearby ------------------------------------------------------------------

    /**
     * Re-scans on a timer while this tab is open, rather than every frame or every open.
     *
     * <p>The scan walks every loaded chunk in a multi-chunk radius; running it at render
     * frequency would make a diagnostic tool into a performance problem in its own right.
     */
    private void refreshNetworksIfDue() {
        long now = System.currentTimeMillis();
        if (now - networksScannedAt < NETWORK_RESCAN_MILLIS && !networks.isEmpty()) {
            return;
        }
        networksScannedAt = now;
        networks = NetworkScanner.scanNearby();
    }

    private void drawNetworks(DrawContext gfx) {
        refreshNetworksIfDue();

        double x = contentX() + PADDING;
        double innerWidth = contentWidth() - PADDING * 2;

        Render2D.pushScissor(gfx, contentX(), bodyTop(), contentWidth(), bodyBottom() - bodyTop());
        double y = bodyTop() + 8 - scroll;

        if (!CreateBridge.isLive()) {
            for (String wrapped : wrap("Create is not installed, or its API did not match this "
                    + "build — nothing to scan. This tab reads real block entities in the "
                    + "chunks around you, so it needs the mod itself, unlike the reference and "
                    + "the planner.", (int) innerWidth)) {
                Render2D.text(gfx, textRenderer, wrapped, x, y, Theme.textMuted());
                y += textRenderer.fontHeight + 1;
            }
            Render2D.popScissor(gfx);
            contentHeightCache = 60;
            return;
        }

        Render2D.text(gfx, textRenderer,
                "Every kinetic network within " + NetworkScanner.SCAN_RADIUS_CHUNKS
                        + " chunks, rescanned every 2 seconds", x, y, Theme.textMuted());
        y += textRenderer.fontHeight + 8;

        if (networks.isEmpty()) {
            Render2D.text(gfx, textRenderer, "No kinetic networks found nearby.", x, y,
                    Theme.textSecondary());
            y += textRenderer.fontHeight;
            Render2D.popScissor(gfx);
            contentHeightCache = y + scroll - bodyTop();
            return;
        }

        for (NetworkScanner.NetworkInfo network : networks) {
            double load = network.load();
            int statusColor = network.overStressed() ? Theme.danger()
                    : load > 0.9 ? Theme.danger()
                    : load > 0.75 ? Theme.warning()
                    : Theme.success();

            Render2D.roundedRect(gfx, x, y, innerWidth, 15, Theme.radiusSmall(),
                    ColorUtil.withAlpha(Theme.surface(), 160));
            Render2D.text(gfx, textRenderer, network.memberCount() + " blocks", x + 6, y + 3,
                    Theme.textPrimary());

            String stress = String.format("%.0f / %.0f su", network.stress(), network.capacity());
            Render2D.text(gfx, textRenderer, stress, x + 90, y + 3, Theme.textSecondary());

            BlockPos closest = network.closestMember();
            String where = closest != null
                    ? closest.getX() + ", " + closest.getY() + ", " + closest.getZ()
                    : "";
            Render2D.textRight(gfx, textRenderer, where, x + innerWidth - 60, y + 3,
                    Theme.textMuted(), false);

            String verdict = network.overStressed() ? "OVERSTRESSED"
                    : String.format("%.0f%%", load * 100);
            Render2D.textRight(gfx, textRenderer, verdict, x + innerWidth, y + 3, statusColor, false);

            y += 18;
        }

        y += 6;
        for (String wrapped : wrap("Only chunks already loaded on your screen are scanned, so a "
                + "network with part of itself unloaded may show a partial member count.",
                (int) innerWidth)) {
            Render2D.text(gfx, textRenderer, wrapped, x, y, Theme.textMuted());
            y += textRenderer.fontHeight + 1;
        }

        Render2D.popScissor(gfx);
        contentHeightCache = y + scroll - bodyTop();
    }

    // -- footer ---------------------------------------------------------------------------

    private void drawFooter(DrawContext gfx) {
        double y = windowY + WINDOW_HEIGHT - FOOTER_HEIGHT;
        Render2D.separator(gfx, contentX(), y, contentWidth(), Theme.border());

        String status;
        int color;
        if (CreateBridge.isLive()) {
            status = "Create detected — stress values read from the mod";
            color = Theme.success();
        } else if (CreateBridge.isCreateLoaded()) {
            status = "Create found, but its API did not match — using built-in figures";
            color = Theme.warning();
        } else {
            status = "Create not installed — planning with built-in figures";
            color = Theme.textMuted();
        }
        Render2D.text(gfx, textRenderer,
                Render2D.truncate(textRenderer, status, (int) (contentWidth() - 96)),
                contentX() + PADDING, y + 8, color);
        Render2D.textRight(gfx, textRenderer, CreateKnowledge.totalEntries() + " entries",
                windowX + WINDOW_WIDTH - PADDING, y + 8, Theme.textMuted(), false);
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

        if (selectedTopic == CALCULATOR_TAB) {
            return handleCalculatorClick(mouseX, mouseY, button);
        }
        if (selectedTopic == RATIO_TAB) {
            return handleRatioClick(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleCalculatorClick(double mouseX, double mouseY, int button) {
        double x = contentX() + PADDING;
        double listWidth = (contentWidth() - PADDING * 2) * 0.52;

        List<StressCalculator.Machine> machines = StressCalculator.MACHINES;
        for (int i = 0; i < machines.size(); i++) {
            double y = machineRowY(i);
            if (MathUtil.within(mouseX, mouseY, x, y, x + listWidth, y + 15)) {
                if (button == 1) {
                    calculator.remove(machines.get(i));
                } else {
                    calculator.add(machines.get(i));
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleRatioClick(double mouseX, double mouseY) {
        if (stepperHit(mouseX, mouseY, ratioRowY(0), true)) {
            sourceRpm = adjustRpm(sourceRpm, -1);
            return true;
        }
        if (stepperHit(mouseX, mouseY, ratioRowY(0), false)) {
            sourceRpm = adjustRpm(sourceRpm, 1);
            return true;
        }
        if (stepperHit(mouseX, mouseY, ratioRowY(1), true)) {
            targetRpm = adjustRpm(targetRpm, -1);
            return true;
        }
        if (stepperHit(mouseX, mouseY, ratioRowY(1), false)) {
            targetRpm = adjustRpm(targetRpm, 1);
            return true;
        }
        if (stepperHit(mouseX, mouseY, windmillRowY, true)) {
            windmillSails = MathUtil.clamp(windmillSails - 4, 0, 256);
            return true;
        }
        if (stepperHit(mouseX, mouseY, windmillRowY, false)) {
            windmillSails = MathUtil.clamp(windmillSails + 4, 0, 256);
            return true;
        }
        return false;
    }

    private boolean stepperHit(double mouseX, double mouseY, double rowY, boolean minus) {
        double x = minus ? stepperMinusX() : stepperPlusX();
        return MathUtil.within(mouseX, mouseY, x, rowY, x + STEPPER_SIZE, rowY + STEPPER_SIZE);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        // Scrolling over a stepper row adjusts that value rather than the page. Without this the
        // steppers would be the only numeric control in the client that cannot be scrubbed.
        if (selectedTopic == RATIO_TAB) {
            int direction = vertical > 0 ? 1 : -1;
            if (overRow(mouseY, ratioRowY(0))) {
                sourceRpm = adjustRpm(sourceRpm, direction);
                return true;
            }
            if (overRow(mouseY, ratioRowY(1))) {
                targetRpm = adjustRpm(targetRpm, direction);
                return true;
            }
            if (overRow(mouseY, windmillRowY)) {
                windmillSails = MathUtil.clamp(windmillSails + 4 * direction, 0, 256);
                return true;
            }
        }
        double viewport = bodyBottom() - bodyTop();
        double max = Math.max(0, contentHeightCache - viewport + 16);
        scroll = MathUtil.clamp(scroll - vertical * 18, 0, max);
        return true;
    }

    private boolean overRow(double mouseY, double rowY) {
        return mouseY >= rowY && mouseY <= rowY + STEPPER_SIZE;
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
