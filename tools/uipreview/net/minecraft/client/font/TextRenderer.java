package net.minecraft.client.font;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
/** Approximates Minecraft's bitmap font metrics closely enough to judge layout. */
public class TextRenderer {
    public int fontHeight = 9;
    private final Graphics2D probe;
    public final Font font;
    public TextRenderer() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        probe = img.createGraphics();
        font = new Font(Font.MONOSPACED, Font.PLAIN, 10);
        probe.setFont(font);
    }
    public int getWidth(String s) { return (int) Math.round(s.length() * 6.0); }
    public String trimToWidth(String s, int width) {
        int chars = Math.max(0, width / 6);
        return s.length() <= chars ? s : s.substring(0, chars);
    }
}
