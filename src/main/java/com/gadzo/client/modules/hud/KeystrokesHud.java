package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.input.InputTracker;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * WASD, mouse-button and sneak/jump key display.
 *
 * <p>Every key owns a press {@link Animation} so a tap fades rather than flickers. The layout
 * is recomputed each frame from the enabled rows, which keeps the grid correct as the user
 * turns individual rows on and off.
 */
public class KeystrokesHud extends HudModule {

    /** The keys this element can draw, in layout order. */
    private enum Slot {
        W, A, S, D, LMB, RMB, SPACE, SHIFT
    }

    /** A positioned key for the current frame. */
    private record Cell(Slot slot, double x, double y, double width, double height) {
    }

    private static final double UNIT = 20.0;
    private static final double GAP = 2.0;
    private static final double GRID_WIDTH = UNIT * 3 + GAP * 2;

    private final BooleanSetting showMouse;
    private final BooleanSetting mouseCps;
    private final BooleanSetting showSpace;
    private final BooleanSetting showSneak;

    private final Map<Slot, Animation> animations = new EnumMap<>(Slot.class);

    public KeystrokesHud() {
        super("Keystrokes", "WASD and mouse button display", HudAnchor.MIDDLE_LEFT, 6, 0);
        this.showMouse = addBool("Mouse buttons", true, "Show LMB and RMB");
        this.mouseCps = add(new BooleanSetting("CPS on mouse", true)
                .<BooleanSetting>describe("Replace LMB/RMB labels with their click rate")
                .visibleWhen(() -> this.showMouse.get()));
        this.showSpace = addBool("Space bar", true, "Show the jump key");
        this.showSneak = addBool("Sneak key", false, "Show the sneak key");

        for (Slot slot : Slot.values()) {
            animations.put(slot, new Animation(0.0, 140L));
        }
    }

    /**
     * Builds the grid for the currently enabled rows.
     *
     * <p>Rows below WASD are appended in order, so disabling the mouse row moves the space
     * bar up rather than leaving a hole.
     */
    private List<Cell> layout() {
        List<Cell> cells = new ArrayList<>(8);
        double row1 = 0;
        double row2 = UNIT + GAP;

        cells.add(new Cell(Slot.W, UNIT + GAP, row1, UNIT, UNIT));
        cells.add(new Cell(Slot.A, 0, row2, UNIT, UNIT));
        cells.add(new Cell(Slot.S, UNIT + GAP, row2, UNIT, UNIT));
        cells.add(new Cell(Slot.D, (UNIT + GAP) * 2, row2, UNIT, UNIT));

        double y = row2 + UNIT + GAP;

        if (showMouse.get()) {
            double half = (GRID_WIDTH - GAP) / 2.0;
            cells.add(new Cell(Slot.LMB, 0, y, half, UNIT));
            cells.add(new Cell(Slot.RMB, half + GAP, y, half, UNIT));
            y += UNIT + GAP;
        }
        if (showSpace.get()) {
            cells.add(new Cell(Slot.SPACE, 0, y, GRID_WIDTH, UNIT));
            y += UNIT + GAP;
        }
        if (showSneak.get()) {
            cells.add(new Cell(Slot.SHIFT, 0, y, GRID_WIDTH, UNIT));
        }
        return cells;
    }

    private static GameOptions options() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.options;
    }

    private static boolean isDown(Function<GameOptions, KeyBinding> pick) {
        GameOptions options = options();
        if (options == null) {
            return false;
        }
        KeyBinding mapping = pick.apply(options);
        return mapping != null && mapping.isPressed();
    }

    private boolean pressed(Slot slot) {
        return switch (slot) {
            case W -> isDown(o -> o.forwardKey);
            case A -> isDown(o -> o.leftKey);
            case S -> isDown(o -> o.backKey);
            case D -> isDown(o -> o.rightKey);
            case LMB -> isDown(o -> o.attackKey);
            case RMB -> isDown(o -> o.useKey);
            case SPACE -> isDown(o -> o.jumpKey);
            case SHIFT -> isDown(o -> o.sneakKey);
        };
    }

    private String label(Slot slot) {
        return switch (slot) {
            case W, A, S, D -> slot.name();
            case LMB -> mouseCps.get() ? InputTracker.leftCps() + " CPS" : "LMB";
            case RMB -> mouseCps.get() ? InputTracker.rightCps() + " CPS" : "RMB";
            case SPACE -> "SPACE";
            case SHIFT -> "SHIFT";
        };
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return GRID_WIDTH;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        List<Cell> cells = layout();
        double bottom = 0;
        for (Cell cell : cells) {
            bottom = Math.max(bottom, cell.y() + cell.height());
        }
        return bottom;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        int index = 0;
        for (Cell cell : layout()) {
            Animation animation = animations.get(cell.slot());
            animation.toBoolean(pressed(cell.slot()));
            double t = animation.value();

            // Released keys sit on a muted plate; pressed keys fill with the accent, phase
            // shifted per row so a gradient accent sweeps across the grid.
            int background = ColorUtil.mix(
                    ColorUtil.withAlpha(Theme.surfaceHigh(), 150),
                    Theme.accent(index * 90),
                    t);
            int textColor = ColorUtil.mix(Theme.textPrimary(), ColorUtil.contrastingText(background), t);

            Render2D.roundedRect(gfx, cell.x(), cell.y(), cell.width(), cell.height(),
                    Theme.radiusSmall(), background);
            Render2D.textCentered(gfx, font, label(cell.slot()),
                    cell.x() + cell.width() / 2.0,
                    cell.y() + (cell.height() - font.fontHeight) / 2.0 + 1,
                    textColor, hasTextShadow());
            index++;
        }
    }
}
