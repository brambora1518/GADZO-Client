import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/** Command-palette mods menu: one column, search-first, settings inline. */
public class Preview {

    static final int W = 900, H = 560;
    static final double PANEL_W = 430, PANEL_H = 396;
    static final double SEARCH_H = 44, ROW_H = 30, HINT_H = 26, PAD = 16;

    static double px, py;

    public static void main(String[] args) throws Exception {
        boolean blurred = args.length > 0 && args[0].equals("blur");
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        drawWorld(img, blurred);
        DrawContext gfx = new DrawContext(img);
        TextRenderer font = new TextRenderer();

        Render2D.rect(gfx, 0, 0, W, H, blurred ? 0x38000000 : 0xB0000000);

        px = (W - PANEL_W) / 2.0;
        py = (H - PANEL_H) * 0.40;

        Render2D.shadow(gfx, px, py, PANEL_W, PANEL_H, Theme.radius(), 12, Theme.shadowColor());
        Render2D.roundedRect(gfx, px, py, PANEL_W, PANEL_H, Theme.radius(), Theme.background());

        drawSearch(gfx, font);
        drawList(gfx, font);
        drawHint(gfx, font);

        Render2D.roundedOutline(gfx, px, py, PANEL_W, PANEL_H, Theme.radius(), 1.0, Theme.glassEdge());
        ImageIO.write(img, "png", new File(args.length > 1 ? args[1] : "preview.png"));
    }

    static void drawSearch(DrawContext gfx, TextRenderer font) {
        double y = py + (SEARCH_H - font.fontHeight) / 2.0;
        // A magnifier drawn from primitives; no texture atlas in the harness or the client.
        double cx = px + PAD + 5, cy = py + SEARCH_H / 2.0 - 1;
        Render2D.circle(gfx, cx, cy, 4.5, Theme.textMuted());
        Render2D.circle(gfx, cx, cy, 3.0, Theme.background());
        Render2D.roundedRect(gfx, cx + 3, cy + 3, 4.5, 1.6, 0.8, Theme.textMuted());

        Render2D.text(gfx, font, "Search modules", px + PAD + 20, y, Theme.textMuted());
        Render2D.rect(gfx, px + PAD + 20 + font.getWidth("Search modules") + 2, py + 14, 1, 16,
                ColorUtil.withAlpha(Theme.accent(), 200));
        Render2D.textRight(gfx, font, "31", px + PANEL_W - PAD, y, Theme.textMuted(), false);
        Render2D.rect(gfx, px + PAD, py + SEARCH_H, PANEL_W - PAD * 2, 1, Theme.glassEdge());
    }

    static final String[][] ROWS = {
        {"Render tuning","Performance","1","1"},
        {"Dynamic FPS","Performance","0","0"},
        {"Entity culling","Performance","1","0"},
        {"Frametime graph","Performance","1","0"},
        {"Waypoints","Survival","1","0"},
        {"Compass","Survival","0","0"},
        {"Light level","Survival","1","0"},
        {"Kinetic readout","Create","1","0"},
        {"Stress alert","Create","0","0"},
        {"Fullbright","Visual","0","0"},
    };

    static double listTop() { return py + SEARCH_H; }
    static double listBottom() { return py + PANEL_H - HINT_H; }

    static void drawList(DrawContext gfx, TextRenderer font) {
        Render2D.pushScissor(gfx, px, listTop(), PANEL_W, listBottom() - listTop());
        double y = py + SEARCH_H + 6;
        for (int i = 0; i < ROWS.length; i++) {
            boolean on = ROWS[i][2].equals("1");
            boolean selected = ROWS[i][3].equals("1");
            boolean hovered = i == 4;

            if (selected || hovered) {
                Render2D.roundedRect(gfx, px + 6, y, PANEL_W - 12, ROW_H, Theme.radiusSmall(),
                        selected ? ColorUtil.withAlpha(Theme.accent(), 28) : Theme.surfaceHover());
            }
            if (selected) {
                Render2D.roundedRect(gfx, px + 6, y + 8, 2.5, ROW_H - 16, 1.25, Theme.accent());
            }

            double ty = y + (ROW_H - font.fontHeight) / 2.0;
            Render2D.text(gfx, font, ROWS[i][0], px + PAD + 4, ty,
                    on ? Theme.textPrimary() : Theme.textSecondary());

            double dotX = px + PANEL_W - PAD - 4;
            Render2D.textRight(gfx, font, ROWS[i][1], dotX - 14, ty, Theme.textMuted(), false);
            if (on) {
                Render2D.circle(gfx, dotX, y + ROW_H / 2.0, 3.2, Theme.accent());
            } else {
                Render2D.circle(gfx, dotX, y + ROW_H / 2.0, 3.2, ColorUtil.withAlpha(Theme.textMuted(), 90));
            }

            y += ROW_H;

            if (selected) y = drawInlineSettings(gfx, font, y);
        }
        Render2D.popScissor(gfx);
    }

    /** Settings for the expanded row, indented under it. */
    static double drawInlineSettings(DrawContext gfx, TextRenderer font, double y) {
        double x = px + PAD + 14, w = PANEL_W - PAD * 2 - 18;
        y += 2;
        drawToggleRow(gfx, font, "Keybind", x, y, w, -1); y += 28;
        drawToggleRow(gfx, font, "Ambient occlusion", x, y, w, 1.0); y += 28;
        drawToggleRow(gfx, font, "Entity shadows", x, y, w, 0.0); y += 28;
        drawSliderRow(gfx, font, "Render distance", "8", x, y, w, 0.42); y += 28;
        drawSliderRow(gfx, font, "Simulation distance", "6", x, y, w, 0.30); y += 28;
        y += 4;
        return y;
    }

    static void drawToggleRow(DrawContext gfx, TextRenderer font, String name, double x, double y, double w, double t) {
        Render2D.text(gfx, font, name, x + 10, y + 5, Theme.textSecondary());
        if (t < 0) {
            double bw = 40;
            Render2D.roundedRect(gfx, x + w - bw, y + 2, bw, 15, Theme.radiusSmall(), Theme.surfaceHigh());
            Render2D.textCentered(gfx, font, "R.SHIFT", x + w - bw / 2, y + 5, Theme.textPrimary(), false);
            return;
        }
        double tw = 24, th = 13;
        int track = ColorUtil.mix(Theme.trackOff(), ColorUtil.withAlpha(Theme.accent(), 184), t);
        Render2D.roundedRect(gfx, x + w - tw, y + 3, tw, th, th / 2.0, track);
        double k = th - 4;
        Render2D.roundedRect(gfx, x + w - tw + 2 + (tw - th) * t, y + 5, k, k, k / 2.0,
                ColorUtil.mix(0xFFCFD6E4, 0xFFFFFFFF, t));
    }

    static void drawSliderRow(DrawContext gfx, TextRenderer font, String name, String val, double x, double y, double w, double f) {
        Render2D.text(gfx, font, name, x + 10, y + 1, Theme.textSecondary());
        Render2D.textRight(gfx, font, val, x + w, y + 1, Theme.textSecondary(), false);
        double ty = y + 16;
        Render2D.roundedRect(gfx, x + 10, ty, w - 10, 3, 1.5, Theme.trackOff());
        double filled = (w - 10) * f;
        if (filled > 0.5) Render2D.roundedRect(gfx, x + 10, ty, filled, 3, 1.5, ColorUtil.withAlpha(Theme.accent(), 200));
        Render2D.roundedRect(gfx, x + 10 + filled - 4.5, ty + 1.5 - 4.5, 9, 9, 4.5, 0xFFF4F6FA);
    }

    static void drawHint(DrawContext gfx, TextRenderer font) {
        double y = py + PANEL_H - HINT_H;
        Render2D.rect(gfx, px + PAD, y, PANEL_W - PAD * 2, 1, ColorUtil.fade(Theme.glassEdge(), 0.6));
        double ty = y + (HINT_H - font.fontHeight) / 2.0 + 1;
        Render2D.text(gfx, font, "enter toggle    tab settings    esc close", px + PAD, ty, Theme.textMuted());
        Render2D.textRight(gfx, font, "GADZO", px + PANEL_W - PAD, ty, ColorUtil.withAlpha(Theme.accent(), 150), false);
    }

    static void drawWorld(BufferedImage img, boolean blur) {
        Random rnd = new Random(7);
        int[][] palette = {{104,158,68},{124,178,80},{88,138,58},{132,116,92},{110,96,76},{150,200,240},{120,170,220}};
        for (int by = 0; by < H; by += 18) for (int bx = 0; bx < W; bx += 18) {
            int[] c = palette[by < H * 0.42 ? 5 + rnd.nextInt(2) : rnd.nextInt(5)];
            int j = rnd.nextInt(22) - 11;
            int col = 0xFF000000 | (cl(c[0]+j) << 16) | (cl(c[1]+j) << 8) | cl(c[2]+j);
            for (int y = by; y < Math.min(by + 18, H); y++)
                for (int x = bx; x < Math.min(bx + 18, W); x++) img.setRGB(x, y, col);
        }
        if (blur) boxBlur(img, 20);
    }
    static int cl(int v) { return Math.max(0, Math.min(255, v)); }

    static void boxBlur(BufferedImage img, int r) {
        for (int pass = 0; pass < 3; pass++) {
            int[] src = img.getRGB(0, 0, W, H, null, 0, W);
            int[] dst = new int[src.length];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                long sr=0,sg=0,sb=0; int n=0;
                for (int k=-r;k<=r;k++){int xx=x+k; if(xx<0||xx>=W)continue; int c=src[y*W+xx];
                    sr+=(c>>16)&0xFF; sg+=(c>>8)&0xFF; sb+=c&0xFF; n++;}
                dst[y*W+x]=0xFF000000|((int)(sr/n)<<16)|((int)(sg/n)<<8)|(int)(sb/n);
            }
            int[] d2 = new int[src.length];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                long sr=0,sg=0,sb=0; int n=0;
                for (int k=-r;k<=r;k++){int yy=y+k; if(yy<0||yy>=H)continue; int c=dst[yy*W+x];
                    sr+=(c>>16)&0xFF; sg+=(c>>8)&0xFF; sb+=c&0xFF; n++;}
                d2[y*W+x]=0xFF000000|((int)(sr/n)<<16)|((int)(sg/n)<<8)|(int)(sb/n);
            }
            img.setRGB(0, 0, W, H, d2, 0, W);
        }
    }
}
