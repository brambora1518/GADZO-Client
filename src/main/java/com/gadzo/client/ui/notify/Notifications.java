package com.gadzo.client.ui.notify;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toast stack in the top-right corner.
 *
 * <p>Toasts slide in from the right, hold for their lifetime, then slide out; the ones below
 * animate upward to close the gap rather than jumping. The stack is capped so a burst of
 * events cannot cover the screen.
 */
public final class Notifications {

    private static final int MAX_VISIBLE = 5;
    private static final double WIDTH = 190.0;
    private static final double HEIGHT = 34.0;
    private static final double MARGIN = 6.0;
    private static final double GAP = 4.0;
    private static final double STRIPE = 3.0;

    private static final List<Notification> ACTIVE = new ArrayList<>();

    private Notifications() {
    }

    public static void register() {
        HudElementRegistry.addLast(GadzoClient.id("notifications"), Notifications::render);
    }

    public static void info(String title, String message) {
        push(new Notification(title, message, Notification.Level.INFO, 3500));
    }

    public static void success(String title, String message) {
        push(new Notification(title, message, Notification.Level.SUCCESS, 3500));
    }

    public static void warning(String title, String message) {
        push(new Notification(title, message, Notification.Level.WARNING, 5000));
    }

    public static void error(String title, String message) {
        push(new Notification(title, message, Notification.Level.ERROR, 6000));
    }

    public static synchronized void push(Notification notification) {
        ACTIVE.add(notification);
        // Retire the oldest rather than refusing the newest: the most recent event is the
        // one the player is most likely waiting on.
        while (ACTIVE.size() > MAX_VISIBLE) {
            ACTIVE.getFirst().beginDismiss();
            break;
        }
    }

    public static synchronized void clear() {
        ACTIVE.clear();
    }

    private static synchronized void render(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();

        Iterator<Notification> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            if (notification.isExpired()) {
                notification.beginDismiss();
            }
            if (notification.isFinished()) {
                iterator.remove();
            }
        }

        double index = 0;
        for (Notification notification : ACTIVE) {
            notification.stackOffset().to(index);
            double slide = notification.slideProgress();
            double y = MARGIN + notification.stackOffset().value() * (HEIGHT + GAP);
            // Slide in from beyond the right edge; alpha tracks the same curve so a toast
            // never appears as a hard-edged rectangle mid-flight.
            double x = screenWidth - MARGIN - WIDTH * slide;

            drawToast(gfx, font, notification, x, y, slide);
            index++;
        }
    }

    private static void drawToast(GuiGraphicsExtractor gfx, Font font, Notification notification,
                                  double x, double y, double alpha) {
        int accent = notification.level().color();

        Render2D.shadow(gfx, x, y, WIDTH, HEIGHT, Theme.radiusSmall(), 4,
                ColorUtil.fade(Theme.shadowColor(), alpha));
        Render2D.roundedRect(gfx, x, y, WIDTH, HEIGHT, Theme.radiusSmall(),
                ColorUtil.fade(Theme.surface(), alpha));

        // Accent stripe down the left edge, rounded only on its outer corners.
        Render2D.roundedRect(gfx, x, y, STRIPE, HEIGHT,
                Theme.radiusSmall(), 0, 0, Theme.radiusSmall(),
                ColorUtil.fade(accent, alpha));

        double textX = x + STRIPE + 7;
        Render2D.text(gfx, font, Render2D.truncate(font, notification.title(), (int) (WIDTH - 24)),
                textX, y + 7, ColorUtil.fade(Theme.textPrimary(), alpha));
        Render2D.text(gfx, font, Render2D.truncate(font, notification.message(), (int) (WIDTH - 24)),
                textX, y + 19, ColorUtil.fade(Theme.textSecondary(), alpha));

        // Countdown bar along the bottom edge.
        double remaining = 1.0 - notification.lifeProgress();
        if (remaining > 0) {
            Render2D.roundedRect(gfx, x + STRIPE, y + HEIGHT - 2,
                    (WIDTH - STRIPE) * remaining, 1.5, 0.75,
                    ColorUtil.fade(accent, alpha * 0.75));
        }
    }
}
