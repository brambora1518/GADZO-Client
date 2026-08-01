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
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference, stress planner and gear-ratio solver for the Create mod.
 *
 * <p>Four tabs of quite different things. The reference is static text. The planner reads its
 * per-block figures out of Create's own registry when the mod is installed, so a pack that
 * retunes stress stays correct, and falls back to built-in defaults for planning without it.
 * The ratio solver is arithmetic and works either way. The network tab is live, and is the only
 * one that needs the mod present.
 *
 * <p>Row geometry is computed once, in the {@code ...RowY} helpers, and shared by the renderer
 * and the click handler. Laying it out twice is how a menu ends up highlighting one row and
 * selecting another.
 */
public class CreateHelperScreen extends GlassScreen {

    private static final int TAB_REFERENCE = 0;
    private static final int TAB_PLANNER = 1;
    private static final int TAB_RATIOS = 2;
    private static final int TAB_NETWORKS = 3;

    /** How often the networks tab re-scans while it is the open tab. */
    private static final long NETWORK_RESCAN_MILLIS = 2000;

    private static final double STEPPER_SIZE = 18;
    private static final double MACHINE_ROW = 16;

    private final StressCalculator calculator = new StressCalculator();

    /** Machine-row hover in the stress planner, keyed by row index. */
    private final Map<Integer, Animation> machineHover = new HashMap<>();

    /** Stepper button hover, keyed by a caller-chosen id ("source-", "source+", ...). */
    private final Map<String, Animation> stepperHover = new HashMap<>();

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
        super("Create");
    }

    @Override
    protected List<String> tabs() {
        return List.of("Reference", "Planner", "Ratios", "Networks");
    }

    @Override
    protected boolean searchable() {
        return activeTab == TAB_REFERENCE;
    }

    @Override
    protected String searchPlaceholder() {
        return "Filter " + CreateKnowledge.totalEntries() + " entries";
    }

    @Override
    protected String headerRight() {
        return switch (activeTab) {
            case TAB_PLANNER -> calculator.totalMachines() + " machines planned";
            case TAB_NETWORKS -> networks.size() + " networks nearby";
            default -> null;
        };
    }

    @Override
    protected String hint() {
        return switch (activeTab) {
            case TAB_PLANNER -> "click add    right-click remove";
            case TAB_RATIOS -> "scroll a row to adjust it";
            case TAB_NETWORKS -> "rescanned every 2 seconds";
            default -> "type to filter    tab next    esc close";
        };
    }

    /**
     * Where the numbers on this screen come from.
     *
     * <p>Worth stating on every tab rather than hiding in the reference: a planner using
     * built-in figures and one reading a retuned pack's real values give different answers, and
     * which of the two you are looking at is not otherwise visible.
     */
    @Override
    protected String hintRight() {
        if (CreateBridge.isLive()) {
            return "live values";
        }
        return CreateBridge.isCreateLoaded() ? "built-in values (API mismatch)" : "built-in values";
    }

    private Animation machineHoverOf(int index) {
        return machineHover.computeIfAbsent(index, ignored -> new Animation(0.0, 120L));
    }

    private Animation stepperHoverOf(String id) {
        return stepperHover.computeIfAbsent(id, ignored -> new Animation(0.0, 120L));
    }

    // -- body -----------------------------------------------------------------------------

    @Override
    protected void drawBody(DrawContext gfx, int mouseX, int mouseY) {
        switch (activeTab) {
            case TAB_PLANNER -> drawPlanner(gfx, mouseX, mouseY);
            case TAB_RATIOS -> drawRatios(gfx, mouseX, mouseY);
            case TAB_NETWORKS -> drawNetworks(gfx);
            default -> drawReference(gfx);
        }
    }

    // -- reference -------------------------------------------------------------------------

    private void drawReference(DrawContext gfx) {
        double x = contentX();
        double innerWidth = contentWidth();
        double y = bodyOrigin();

        if (!searchQuery.isBlank()) {
            List<CreateKnowledge.Entry> matches = CreateKnowledge.searchAll(searchQuery);
            if (matches.isEmpty()) {
                body(gfx, "Nothing matches that.", x, y);
                contentHeight = 40;
                return;
            }
            for (CreateKnowledge.Entry entry : matches) {
                y = drawEntry(gfx, entry, x, y, innerWidth);
            }
            contentHeight = y + scroll - bodyTop();
            return;
        }

        for (CreateKnowledge.Topic topic : CreateKnowledge.topics()) {
            sectionLabel(gfx, topic.name(), x, y);
            y += textRenderer.fontHeight + 7;
            for (CreateKnowledge.Entry entry : topic.entries()) {
                y = drawEntry(gfx, entry, x, y, innerWidth);
            }
            y += 8;
        }
        contentHeight = y + scroll - bodyTop();
    }

    private double drawEntry(DrawContext gfx, CreateKnowledge.Entry entry, double x, double y,
                             double innerWidth) {
        Render2D.text(gfx, textRenderer, entry.title(), x, y,
                ColorUtil.fade(Theme.textPrimary(), openAlpha));
        y += textRenderer.fontHeight + 4;

        for (String paragraph : entry.body()) {
            // Wrap by hand: the vanilla wrapper works on Text objects and this is plain text.
            for (String wrapped : wrap(paragraph, (int) innerWidth)) {
                body(gfx, wrapped, x, y);
                y += textRenderer.fontHeight + 1;
            }
            y += 3;
        }
        return y + 8;
    }

    // -- stress planner ---------------------------------------------------------------------

    private double machineRowY(int index) {
        return bodyOrigin() + index * MACHINE_ROW;
    }

    private double listWidth() {
        return contentWidth() * 0.50;
    }

    private void drawPlanner(DrawContext gfx, int mouseX, int mouseY) {
        double x = contentX();
        double innerWidth = contentWidth();
        double listWidth = listWidth();

        List<StressCalculator.Machine> machines = StressCalculator.MACHINES;
        double listBottom = bodyTop();

        for (int i = 0; i < machines.size(); i++) {
            StressCalculator.Machine machine = machines.get(i);
            double y = machineRowY(i);

            boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + listWidth, y + MACHINE_ROW);
            Animation hover = machineHoverOf(i);
            hover.toBoolean(hovered);
            if (hover.value() > 0.01) {
                Render2D.roundedRect(gfx, x - 6, y - 1, listWidth + 8, MACHINE_ROW,
                        Theme.radiusSmall(),
                        ColorUtil.fade(Theme.surfaceHover(), hover.value() * openAlpha));
            }

            int count = countOf(machine);
            double textY = y + (MACHINE_ROW - textRenderer.fontHeight) / 2.0 - 1;
            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, machine.name(), (int) (listWidth - 70)),
                    x, textY,
                    ColorUtil.fade(count > 0 ? Theme.textPrimary() : Theme.textSecondary(), openAlpha));
            Render2D.textRight(gfx, textRenderer, String.format("%.0f /rpm", machine.impact()),
                    x + listWidth - 26, textY, ColorUtil.fade(Theme.textMuted(), openAlpha), false);
            if (count > 0) {
                Render2D.textRight(gfx, textRenderer, "x" + count, x + listWidth, textY,
                        ColorUtil.fade(Theme.accent(), openAlpha), false);
            }
            listBottom = y + MACHINE_ROW;
        }

        // Summary column.
        double summaryX = x + listWidth + 20;
        double summaryWidth = innerWidth - listWidth - 20;
        double summaryY = bodyOrigin();

        sectionLabel(gfx, "Plan", summaryX, summaryY);
        summaryY += textRenderer.fontHeight + 7;

        keyValue(gfx, "machines", Integer.toString(calculator.totalMachines()),
                summaryX, summaryX + summaryWidth, summaryY);
        summaryY += textRenderer.fontHeight + 3;
        keyValue(gfx, "impact", String.format("%.0f su/rpm", calculator.totalImpact()),
                summaryX, summaryX + summaryWidth, summaryY);
        summaryY += textRenderer.fontHeight + 3;
        keyValue(gfx, "at 64 rpm", String.format("%.0f SU", calculator.stressAtSpeed(64)),
                summaryX, summaryX + summaryWidth, summaryY);
        summaryY += textRenderer.fontHeight + 14;

        sectionLabel(gfx, "Generators needed", summaryX, summaryY);
        summaryY += textRenderer.fontHeight + 7;

        for (StressCalculator.Generator generator : StressCalculator.GENERATORS) {
            int needed = calculator.generatorsNeeded(generator);
            Render2D.text(gfx, textRenderer,
                    Render2D.truncate(textRenderer, generator.name(), (int) (summaryWidth - 30)),
                    summaryX, summaryY,
                    ColorUtil.fade(Theme.textSecondary(), openAlpha));
            Render2D.textRight(gfx, textRenderer, needed == 0 ? "—" : "x" + needed,
                    summaryX + summaryWidth, summaryY,
                    ColorUtil.fade(needed == 0 ? Theme.textMuted() : Theme.textPrimary(), openAlpha),
                    false);
            summaryY += textRenderer.fontHeight + 1;

            Render2D.text(gfx, textRenderer,
                    String.format("%.0f SU at %d rpm", generator.totalStressUnits(), generator.rpm()),
                    summaryX + 8, summaryY, ColorUtil.fade(Theme.textMuted(), openAlpha));
            summaryY += textRenderer.fontHeight + 5;
        }

        summaryY += 6;
        for (String wrapped : wrap("Gearing the whole network up changes nothing: impact and "
                + "capacity both scale with RPM. Gearing up only the machines does raise the "
                + "load.", (int) summaryWidth)) {
            Render2D.text(gfx, textRenderer, wrapped, summaryX, summaryY,
                    ColorUtil.fade(Theme.textMuted(), openAlpha));
            summaryY += textRenderer.fontHeight;
        }

        contentHeight = Math.max(listBottom, summaryY) + scroll - bodyTop();
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
        return bodyOrigin() + row * 24;
    }

    private void drawRatios(DrawContext gfx, int mouseX, int mouseY) {
        double x = contentX();
        double innerWidth = contentWidth();

        drawStepper(gfx, "Source speed", sourceRpm + " RPM", ratioRowY(0), mouseX, mouseY);
        drawStepper(gfx, "Target speed", targetRpm + " RPM", ratioRowY(1), mouseX, mouseY);

        double y = ratioRowY(2) + 8;
        GearTrain.Plan plan = GearTrain.solve(sourceRpm, targetRpm);

        sectionLabel(gfx, "Result", x, y);
        y += textRenderer.fontHeight + 7;

        Render2D.text(gfx, textRenderer,
                String.format("%d RPM — %s", (int) plan.resultRpm(),
                        plan.exact() ? "exact" : "closest reachable"),
                x, y, ColorUtil.fade(plan.exact() ? Theme.success() : Theme.warning(), openAlpha));
        y += textRenderer.fontHeight + 6;

        if (plan.steps().isEmpty()) {
            body(gfx, "No cogwheel pairs needed.", x, y);
            y += textRenderer.fontHeight + 2;
        } else {
            int step = 1;
            for (GearTrain.Step gearStep : plan.steps()) {
                body(gfx, step + ".  " + gearStep.description(), x, y);
                y += textRenderer.fontHeight + 2;
                step++;
            }
            Render2D.text(gfx, textRenderer,
                    plan.reversesDirection()
                            ? "Output turns the opposite way to the source."
                            : "Output turns the same way as the source.",
                    x, y, ColorUtil.fade(Theme.textMuted(), openAlpha));
            y += textRenderer.fontHeight + 2;
        }

        y += 6;
        for (String wrapped : wrap(plan.note(), (int) innerWidth)) {
            Render2D.text(gfx, textRenderer, wrapped, x, y,
                    ColorUtil.fade(plan.exact() ? Theme.textSecondary() : Theme.warning(), openAlpha));
            y += textRenderer.fontHeight + 1;
        }

        y += 16;
        sectionLabel(gfx, "Windmill", x, y);
        y += textRenderer.fontHeight + 7;

        windmillRowY = y;
        drawStepper(gfx, "Sails", Integer.toString(windmillSails), y, mouseX, mouseY);
        y += 26;

        for (String wrapped : wrap(GearTrain.windmillAdvice(windmillSails), (int) innerWidth)) {
            body(gfx, wrapped, x, y);
            y += textRenderer.fontHeight + 1;
        }

        contentHeight = y + scroll - bodyTop();
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
        Render2D.text(gfx, textRenderer, label, contentX(), y + 5,
                ColorUtil.fade(Theme.textSecondary(), openAlpha));

        double minusX = stepperMinusX();
        double plusX = stepperPlusX();

        drawStepperButton(gfx, "−", minusX, y, mouseX, mouseY, label + "-");
        drawStepperButton(gfx, "+", plusX, y, mouseX, mouseY, label + "+");

        Render2D.textCentered(gfx, textRenderer, value,
                (minusX + STEPPER_SIZE + plusX) / 2.0, y + 5,
                ColorUtil.fade(Theme.textPrimary(), openAlpha), false);
    }

    private double stepperMinusX() {
        return contentX() + 92;
    }

    private double stepperPlusX() {
        return contentX() + 168;
    }

    /**
     * A stepper button.
     *
     * <p>Drawn as a tinted disc rather than a bordered square: it is the only pressable control
     * on these screens, and one soft shape reads as a control without adding an edge.
     */
    private void drawStepperButton(DrawContext gfx, String glyph, double x, double y,
                                   int mouseX, int mouseY, String hoverId) {
        boolean hovered = MathUtil.within(mouseX, mouseY, x, y, x + STEPPER_SIZE, y + STEPPER_SIZE);
        Animation hover = stepperHoverOf(hoverId);
        hover.toBoolean(hovered);

        int fill = ColorUtil.mix(Theme.surfaceHover(),
                ColorUtil.withAlpha(Theme.accent(), 70), hover.value());
        Render2D.circle(gfx, x + STEPPER_SIZE / 2.0, y + STEPPER_SIZE / 2.0, STEPPER_SIZE / 2.0,
                ColorUtil.fade(fill, openAlpha));
        Render2D.textCentered(gfx, textRenderer, glyph, x + STEPPER_SIZE / 2.0, y + 5,
                ColorUtil.fade(Theme.textPrimary(), openAlpha), false);
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

        double x = contentX();
        double innerWidth = contentWidth();
        double y = bodyOrigin();

        if (!CreateBridge.isLive()) {
            for (String wrapped : wrap("Create is not installed, or its API did not match this "
                    + "build — nothing to scan. This tab reads real block entities in the "
                    + "chunks around you, so it needs the mod itself, unlike the reference and "
                    + "the planner.", (int) innerWidth)) {
                Render2D.text(gfx, textRenderer, wrapped, x, y,
                        ColorUtil.fade(Theme.textMuted(), openAlpha));
                y += textRenderer.fontHeight + 1;
            }
            contentHeight = 60;
            return;
        }

        if (networks.isEmpty()) {
            body(gfx, "No kinetic networks found within " + NetworkScanner.SCAN_RADIUS_CHUNKS
                    + " chunks.", x, y);
            contentHeight = 40;
            return;
        }

        for (NetworkScanner.NetworkInfo network : networks) {
            double load = network.load();
            int statusColor = network.overStressed() || load > 0.9 ? Theme.danger()
                    : load > 0.75 ? Theme.warning()
                    : Theme.success();

            Render2D.text(gfx, textRenderer, network.memberCount() + " blocks", x, y,
                    ColorUtil.fade(Theme.textPrimary(), openAlpha));

            BlockPos closest = network.closestMember();
            if (closest != null) {
                Render2D.text(gfx, textRenderer,
                        closest.getX() + ", " + closest.getY() + ", " + closest.getZ(),
                        x + 74, y, ColorUtil.fade(Theme.textMuted(), openAlpha));
            }

            String verdict = network.overStressed() ? "overstressed"
                    : String.format("%.0f%%", load * 100);
            Render2D.textRight(gfx, textRenderer, verdict, x + innerWidth, y,
                    ColorUtil.fade(statusColor, openAlpha), false);
            y += textRenderer.fontHeight + 4;

            // The load bar carries the reading; the numbers underneath are for planning.
            bar(gfx, x, y, innerWidth, 3, load, statusColor);
            y += 7;

            Render2D.text(gfx, textRenderer,
                    String.format("%.0f / %.0f su", network.stress(), network.capacity()),
                    x, y, ColorUtil.fade(Theme.textSecondary(), openAlpha));
            y += textRenderer.fontHeight + 12;
        }

        y += 4;
        for (String wrapped : wrap("Only chunks already loaded on your screen are scanned, so a "
                + "network with part of itself unloaded may show a partial member count.",
                (int) innerWidth)) {
            Render2D.text(gfx, textRenderer, wrapped, x, y,
                    ColorUtil.fade(Theme.textMuted(), openAlpha));
            y += textRenderer.fontHeight + 1;
        }

        contentHeight = y + scroll - bodyTop();
    }

    // -- input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeTab == TAB_PLANNER && handlePlannerClick(mouseX, mouseY, button)) {
            return true;
        }
        if (activeTab == TAB_RATIOS && handleRatioClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handlePlannerClick(double mouseX, double mouseY, int button) {
        double x = contentX();
        double listWidth = listWidth();

        List<StressCalculator.Machine> machines = StressCalculator.MACHINES;
        for (int i = 0; i < machines.size(); i++) {
            double y = machineRowY(i);
            if (MathUtil.within(mouseX, mouseY, x, y, x + listWidth, y + MACHINE_ROW)) {
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
        if (activeTab == TAB_RATIOS) {
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
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    private boolean overRow(double mouseY, double rowY) {
        return mouseY >= rowY && mouseY <= rowY + STEPPER_SIZE;
    }
}
