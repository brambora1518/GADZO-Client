package com.gadzo.client.ui.screen;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.hud.HudManager;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag-and-drop editor for HUD element placement.
 *
 * <p>Elements are drawn live so what the player arranges is exactly what they get. Dragging
 * snaps to screen edges, to the screen centre lines, and to the edges of other elements —
 * with a guide line drawn for whichever snap is active, so alignment is visible rather than
 * guessed. On release the element re-anchors to its nearest corner, which is what keeps a
 * layout correct across resolutions.
 */
public class HudEditorScreen extends Screen {

    /** How close, in pixels, a drag must come before it snaps. */
    private static final double SNAP_DISTANCE = 6.0;

    /** Fades the dim and the help bar in; the outlines and elements themselves need no fade. */
    private final Animation openAnimation = new Animation(0.0, 220L);

    private HudModule dragged;

    /** Grab point within the dragged element, so it does not jump to the cursor. */
    private double grabOffsetX;
    private double grabOffsetY;

    /** Guide lines to draw this frame, rebuilt on every drag step. */
    private final List<Double> verticalGuides = new ArrayList<>();
    private final List<Double> horizontalGuides = new ArrayList<>();

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        HudManager.setEditorOpen(true);
        openAnimation.to(1.0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private List<HudModule> editableElements() {
        List<HudModule> elements = new ArrayList<>();
        for (HudModule module : GadzoClient.modules().hudModules()) {
            if (module.isEnabled()) {
                elements.add(module);
            }
        }
        return elements;
    }

    // -- rendering ---------------------------------------------------------------------------

    /**
     * Frosted backdrop.
     *
     * <p>See {@code ClickGuiScreen.extractBackground}: vanilla's default background requests
     * a blur too, and only one is permitted per frame.
     */
    private void drawBackdrop(DrawContext gfx, double open) {
        if (Theme.blurEnabled()) {
            Render2D.blurBehind(gfx);
        }
        Render2D.rect(gfx, 0, 0, width, height, ColorUtil.fade(0x99000000, open));
    }

    @Override
    public void render(DrawContext gfx, int mouseX, int mouseY, float partialTick) {
        double open = openAnimation.value();
        drawBackdrop(gfx, open);
        drawCenterLines(gfx);

        for (HudModule module : editableElements()) {
            module.render(gfx, textRenderer, width, height, 1.0);
            drawOutline(gfx, module, mouseX, mouseY);
        }

        drawGuides(gfx);
        drawHelpBar(gfx, open);
    }

    private void drawCenterLines(DrawContext gfx) {
        int subtle = ColorUtil.withAlpha(Theme.textMuted(), 40);
        Render2D.rect(gfx, width / 2.0, 0, 1, height, subtle);
        Render2D.rect(gfx, 0, height / 2.0, width, 1, subtle);
    }

    private void drawOutline(DrawContext gfx, HudModule module, int mouseX, int mouseY) {
        double x = module.lastX();
        double y = module.lastY();
        double w = module.lastWidth();
        double h = module.lastHeight();

        boolean hovered = module.containsPoint(mouseX, mouseY);
        boolean active = module == dragged;

        int color = active ? Theme.accent() : (hovered ? Theme.textSecondary() : Theme.border());
        Render2D.roundedOutline(gfx, x - 1, y - 1, w + 2, h + 2, Theme.radiusSmall(), 1.0, color);

        if (hovered || active) {
            // Floating label so the player can tell overlapping elements apart.
            String label = module.getName();
            double labelWidth = textRenderer.getWidth(label) + 8;
            double labelY = y - textRenderer.fontHeight - 4;
            if (labelY < 0) {
                labelY = y + h + 3;
            }
            Render2D.roundedRect(gfx, x - 1, labelY, labelWidth, textRenderer.fontHeight + 2,
                    Theme.radiusSmall(), ColorUtil.withAlpha(Theme.surface(), 235));
            Render2D.text(gfx, textRenderer, label, x + 3, labelY + 2, Theme.textPrimary());
        }
    }

    private void drawGuides(DrawContext gfx) {
        int color = Theme.accent();
        for (double x : verticalGuides) {
            Render2D.rect(gfx, x, 0, 1, height, ColorUtil.withAlpha(color, 190));
        }
        for (double y : horizontalGuides) {
            Render2D.rect(gfx, 0, y, width, 1, ColorUtil.withAlpha(color, 190));
        }
    }

    private void drawHelpBar(DrawContext gfx, double open) {
        String help = "Drag to move  ·  arrows nudge  ·  R resets selected  ·  Esc saves and exits";
        double barWidth = textRenderer.getWidth(help) + 20;
        double x = (width - barWidth) / 2.0;
        // Rises the last few pixels into place rather than appearing fully formed, so the one
        // piece of chrome on an otherwise bare editing surface still reads as considered.
        double y = height - 26 + (1.0 - open) * 8.0;

        Render2D.shadow(gfx, x, y, barWidth, 18, Theme.radiusSmall(), 4,
                ColorUtil.fade(Theme.shadowColor(), open));
        Render2D.roundedRect(gfx, x, y, barWidth, 18, Theme.radiusSmall(),
                ColorUtil.fade(ColorUtil.withAlpha(Theme.surface(), 240), open));
        Render2D.textCentered(gfx, textRenderer, help, width / 2.0, y + 5,
                ColorUtil.fade(Theme.textSecondary(), open), false);
    }

    // -- snapping ------------------------------------------------------------------------------

    /**
     * Applies snapping to a proposed top-left position.
     *
     * <p>Candidate lines come from the screen edges and centre, plus the edges and centres of
     * every other visible element. The closest candidate within {@link #SNAP_DISTANCE} wins on
     * each axis independently, so an element can snap horizontally without being pulled
     * vertically.
     *
     * @return the adjusted position as {@code [x, y]}
     */
    private double[] applySnapping(HudModule moving, double proposedX, double proposedY) {
        verticalGuides.clear();
        horizontalGuides.clear();

        double w = moving.totalWidth(textRenderer);
        double h = moving.totalHeight(textRenderer);

        List<Double> xCandidates = new ArrayList<>(List.of(0.0, width / 2.0, (double) width));
        List<Double> yCandidates = new ArrayList<>(List.of(0.0, height / 2.0, (double) height));

        for (HudModule other : editableElements()) {
            if (other == moving) {
                continue;
            }
            xCandidates.add(other.lastX());
            xCandidates.add(other.lastX() + other.lastWidth());
            yCandidates.add(other.lastY());
            yCandidates.add(other.lastY() + other.lastHeight());
        }

        double snappedX = snapAxis(proposedX, w, xCandidates, verticalGuides);
        double snappedY = snapAxis(proposedY, h, yCandidates, horizontalGuides);
        return new double[]{snappedX, snappedY};
    }

    /**
     * Snaps one axis, testing the element's leading edge, centre and trailing edge.
     *
     * @param guides receives the line to draw when a snap is taken
     */
    private double snapAxis(double position, double size, List<Double> candidates, List<Double> guides) {
        double best = position;
        double bestDistance = SNAP_DISTANCE;
        double guide = Double.NaN;

        // Offsets of the element's own reference points relative to its top-left.
        double[] edges = {0.0, size / 2.0, size};

        for (double candidate : candidates) {
            for (double edge : edges) {
                double target = candidate - edge;
                double distance = Math.abs(position - target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = target;
                    guide = candidate;
                }
            }
        }

        if (!Double.isNaN(guide)) {
            guides.add(guide);
        }
        return best;
    }

    // -- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double clickX, double clickY, int button) {
        double mouseX = clickX;
        double mouseY = clickY;

        // Iterate in reverse so the element drawn last (on top) is grabbed first.
        List<HudModule> elements = editableElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudModule module = elements.get(i);
            if (module.containsPoint(mouseX, mouseY)) {
                dragged = module;
                grabOffsetX = mouseX - module.lastX();
                grabOffsetY = mouseY - module.lastY();
                return true;
            }
        }
        return super.mouseClicked(clickX, clickY, button);
    }

    @Override
    public boolean mouseDragged(double clickX, double clickY, int button, double deltaX, double deltaY) {
        if (dragged == null) {
            return super.mouseDragged(clickX, clickY, button, deltaX, deltaY);
        }

        double proposedX = clickX - grabOffsetX;
        double proposedY = clickY - grabOffsetY;

        double[] snapped = applySnapping(dragged, proposedX, proposedY);

        // Keep the element on screen regardless of where the cursor went.
        double w = dragged.totalWidth(textRenderer);
        double h = dragged.totalHeight(textRenderer);
        double x = MathUtil.clamp(snapped[0], 0, Math.max(0, width - w));
        double y = MathUtil.clamp(snapped[1], 0, Math.max(0, height - h));

        dragged.moveTopLeftTo(textRenderer, x, y, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(double clickX, double clickY, int button) {
        if (dragged != null) {
            // Re-anchor now the final position is known, so the element keeps sensible
            // behaviour if the window is later resized.
            dragged.reanchor(textRenderer, width, height);
            dragged = null;
            verticalGuides.clear();
            horizontalGuides.clear();
            return true;
        }
        return super.mouseReleased(clickX, clickY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        HudModule target = dragged != null ? dragged : hoveredElement();
        if (target != null) {
            int step = hasShiftDown() ? 10 : 1;
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> {
                    nudge(target, -step, 0);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    nudge(target, step, 0);
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    nudge(target, 0, -step);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    nudge(target, 0, step);
                    return true;
                }
                case GLFW.GLFW_KEY_R -> {
                    target.setOffset(0, 0);
                    return true;
                }
                default -> {
                    // Fall through to the default handling below.
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The element under the cursor, used so arrow keys work without holding a drag. */
    private HudModule hoveredElement() {
        if (client == null) {
            return null;
        }
        // 1.20.1 has no scaled-cursor helper, so convert from window to GUI coordinates.
        double scaleX = client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
        double scaleY = client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();
        double mouseX = client.mouse.getX() * scaleX;
        double mouseY = client.mouse.getY() * scaleY;

        List<HudModule> elements = editableElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            if (elements.get(i).containsPoint(mouseX, mouseY)) {
                return elements.get(i);
            }
        }
        return null;
    }

    private void nudge(HudModule module, double dx, double dy) {
        module.setOffset(module.getOffsetX() + dx, module.getOffsetY() + dy);
    }

    @Override
    public void removed() {
        HudManager.setEditorOpen(false);
        ConfigManager.save();
        super.removed();
    }
}
