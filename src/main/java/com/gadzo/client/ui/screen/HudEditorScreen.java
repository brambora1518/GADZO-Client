package com.gadzo.client.ui.screen;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.hud.HudManager;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import com.gadzo.client.util.MathUtil;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

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

    private HudModule dragged;

    /** Grab point within the dragged element, so it does not jump to the cursor. */
    private double grabOffsetX;
    private double grabOffsetY;

    /** Guide lines to draw this frame, rebuilt on every drag step. */
    private final List<Double> verticalGuides = new ArrayList<>();
    private final List<Double> horizontalGuides = new ArrayList<>();

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        HudManager.setEditorOpen(true);
    }

    @Override
    public boolean isPauseScreen() {
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (Theme.blurEnabled()) {
            Render2D.blurBehind(gfx);
        }
        Render2D.rect(gfx, 0, 0, width, height, 0x99000000);

        drawCenterLines(gfx);

        for (HudModule module : editableElements()) {
            module.render(gfx, font, width, height, 1.0);
            drawOutline(gfx, module, mouseX, mouseY);
        }

        drawGuides(gfx);
        drawHelpBar(gfx);
    }

    private void drawCenterLines(GuiGraphicsExtractor gfx) {
        int subtle = ColorUtil.withAlpha(Theme.textMuted(), 40);
        Render2D.rect(gfx, width / 2.0, 0, 1, height, subtle);
        Render2D.rect(gfx, 0, height / 2.0, width, 1, subtle);
    }

    private void drawOutline(GuiGraphicsExtractor gfx, HudModule module, int mouseX, int mouseY) {
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
            double labelWidth = font.width(label) + 8;
            double labelY = y - font.lineHeight - 4;
            if (labelY < 0) {
                labelY = y + h + 3;
            }
            Render2D.roundedRect(gfx, x - 1, labelY, labelWidth, font.lineHeight + 2,
                    Theme.radiusSmall(), ColorUtil.withAlpha(Theme.surface(), 235));
            Render2D.text(gfx, font, label, x + 3, labelY + 2, Theme.textPrimary());
        }
    }

    private void drawGuides(GuiGraphicsExtractor gfx) {
        int color = Theme.accent();
        for (double x : verticalGuides) {
            Render2D.rect(gfx, x, 0, 1, height, ColorUtil.withAlpha(color, 190));
        }
        for (double y : horizontalGuides) {
            Render2D.rect(gfx, 0, y, width, 1, ColorUtil.withAlpha(color, 190));
        }
    }

    private void drawHelpBar(GuiGraphicsExtractor gfx) {
        String help = "Drag to move  ·  arrows nudge  ·  R resets selected  ·  Esc saves and exits";
        double barWidth = font.width(help) + 20;
        double x = (width - barWidth) / 2.0;
        double y = height - 26;

        Render2D.shadow(gfx, x, y, barWidth, 18, Theme.radiusSmall(), 4, Theme.shadowColor());
        Render2D.roundedRect(gfx, x, y, barWidth, 18, Theme.radiusSmall(),
                ColorUtil.withAlpha(Theme.surface(), 240));
        Render2D.textCentered(gfx, font, help, width / 2.0, y + 5, Theme.textSecondary(), false);
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

        double w = moving.totalWidth(font);
        double h = moving.totalHeight(font);

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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragged == null) {
            return super.mouseDragged(event, deltaX, deltaY);
        }

        double proposedX = event.x() - grabOffsetX;
        double proposedY = event.y() - grabOffsetY;

        double[] snapped = applySnapping(dragged, proposedX, proposedY);

        // Keep the element on screen regardless of where the cursor went.
        double w = dragged.totalWidth(font);
        double h = dragged.totalHeight(font);
        double x = MathUtil.clamp(snapped[0], 0, Math.max(0, width - w));
        double y = MathUtil.clamp(snapped[1], 0, Math.max(0, height - h));

        dragged.moveTopLeftTo(font, x, y, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragged != null) {
            // Re-anchor now the final position is known, so the element keeps sensible
            // behaviour if the window is later resized.
            dragged.reanchor(font, width, height);
            dragged = null;
            verticalGuides.clear();
            horizontalGuides.clear();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        HudModule target = dragged != null ? dragged : hoveredElement();
        if (target != null) {
            int step = minecraft != null && minecraft.hasShiftDown() ? 10 : 1;
            switch (event.key()) {
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
        return super.keyPressed(event);
    }

    /** The element under the cursor, used so arrow keys work without holding a drag. */
    private HudModule hoveredElement() {
        if (minecraft == null) {
            return null;
        }
        double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());

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
