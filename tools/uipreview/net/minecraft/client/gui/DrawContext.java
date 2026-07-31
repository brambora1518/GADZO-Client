package net.minecraft.client.gui;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Writes Render2D's output into an image so the result can actually be looked at. */
public class DrawContext {
    public final BufferedImage image;
    private final MatrixStack matrices = new MatrixStack();
    private Rectangle scissor;

    public DrawContext(BufferedImage image) { this.image = image; }
    public MatrixStack getMatrices() { return matrices; }
    public void draw() {}
    public void enableScissor(int x1, int y1, int x2, int y2) { scissor = new Rectangle(x1, y1, x2 - x1, y2 - y1); }
    public void disableScissor() { scissor = null; }

    private void blend(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return;
        if (scissor != null && !scissor.contains(x, y)) return;
        int sa = (argb >>> 24) & 0xFF;
        if (sa == 0) return;
        int dst = image.getRGB(x, y);
        double a = sa / 255.0;
        int r = (int) Math.round(((argb >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * (1 - a));
        int g = (int) Math.round(((argb >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * (1 - a));
        int b = (int) Math.round((argb & 0xFF) * a + (dst & 0xFF) * (1 - a));
        image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) blend(x, y, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        int rows = Math.max(1, y2 - y1);
        for (int y = y1; y < y2; y++) {
            double t = (y - y1) / (double) rows;
            int a = (int) Math.round(lerp((top >>> 24) & 0xFF, (bottom >>> 24) & 0xFF, t));
            int r = (int) Math.round(lerp((top >> 16) & 0xFF, (bottom >> 16) & 0xFF, t));
            int g = (int) Math.round(lerp((top >> 8) & 0xFF, (bottom >> 8) & 0xFF, t));
            int b = (int) Math.round(lerp(top & 0xFF, bottom & 0xFF, t));
            int c = (a << 24) | (r << 16) | (g << 8) | b;
            for (int x = x1; x < x2; x++) blend(x, y, c);
        }
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    public void drawText(TextRenderer font, String text, int x, int y, int color, boolean shadow) {
        Graphics2D g = image.createGraphics();
        if (scissor != null) g.setClip(scissor);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font.font);
        if (shadow) {
            g.setColor(new Color(0, 0, 0, 140));
            g.drawString(text, x + 1, y + 8);
        }
        g.setColor(new Color(color, true));
        g.drawString(text, x, y + 7);
        g.dispose();
    }
}
