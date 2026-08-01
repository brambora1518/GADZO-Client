import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The helper screens' shared chrome: header, text tab strip, scrolling body, hint row.
 *
 * <p>Geometry is copied constant-for-constant from {@code GlassScreen} so this shows the real
 * layout rather than an impression of it. Content is a sample of the Create planner tab.
 */
public class HelperPreview {

    static final int W = 900, H = 560;

    // Mirrors GlassScreen.
    static final double PANEL_W = 520, PANEL_H = 420;
    static final double HEADER_H = 44, TABS_H = 32, HINT_H = 26, PAD = 16, TAB_GAP = 20;

    static double px, py;

    public static void main(String[] args) throws Exception {
        boolean blurred = args.length > 0 && args[0].equals("blur");
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Preview.drawWorld(img, blurred);
        DrawContext gfx = new DrawContext(img);
        TextRenderer font = new TextRenderer();

        Render2D.rect(gfx, 0, 0, W, H, blurred ? 0x38000000 : 0xB0000000);

        px = (W - PANEL_W) / 2.0;
        py = (H - PANEL_H) * 0.40;

        Render2D.shadow(gfx, px, py, PANEL_W, PANEL_H, Theme.radius(), 12, Theme.shadowColor());
        Render2D.roundedRect(gfx, px, py, PANEL_W, PANEL_H, Theme.radius(), Theme.background());

        drawHeader(gfx, font);
        drawTabs(gfx, font);
        drawPlanner(gfx, font);
        drawHint(gfx, font);

        Render2D.roundedOutline(gfx, px, py, PANEL_W, PANEL_H, Theme.radius(), 1.0, Theme.glassEdge());
        ImageIO.write(img, "png", new File(args.length > 1 ? args[1] : "helper.png"));
    }

    static double cx() { return px + PAD; }
    static double cw() { return PANEL_W - PAD * 2; }
    static double bodyTop() { return py + HEADER_H + TABS_H; }

    static void drawHeader(DrawContext gfx, TextRenderer font) {
        double y = py + (HEADER_H - font.fontHeight) / 2.0;
        Render2D.text(gfx, font, "Create", cx(), y, Theme.textPrimary());
        Render2D.textRight(gfx, font, "6 machines planned", px + PANEL_W - PAD, y,
                Theme.textMuted(), false);
    }

    static final String[] TABS = {"Reference", "Planner", "Ratios", "Networks"};
    static final int ACTIVE = 1;

    static void drawTabs(DrawContext gfx, TextRenderer font) {
        double y = py + HEADER_H;
        double textY = y + (TABS_H - font.fontHeight) / 2.0 - 1;
        double x = cx();
        for (int i = 0; i < TABS.length; i++) {
            double w = font.getWidth(TABS[i]);
            boolean active = i == ACTIVE;
            Render2D.text(gfx, font, TABS[i], x, textY,
                    active ? Theme.textPrimary() : Theme.textMuted());
            if (active) {
                Render2D.roundedRect(gfx, x, y + TABS_H - 7, w, 2.0, 1.0, Theme.accent());
            }
            x += w + TAB_GAP;
        }
        Render2D.rect(gfx, cx(), y + TABS_H - 1, cw(), 1,
                ColorUtil.withAlpha(Theme.glassEdge(), 0xB0));
    }

    static final String[][] MACHINES = {
        {"Millstone",       "4",  "2"},
        {"Crushing Wheel",  "8",  "0"},
        {"Mechanical Press","8",  "2"},
        {"Mechanical Mixer","4",  "1"},
        {"Mechanical Saw",  "4",  "0"},
        {"Mechanical Drill","4",  "1"},
        {"Encased Fan",     "2",  "0"},
        {"Mechanical Arm",  "2",  "0"},
        {"Deployer",        "4",  "0"},
        {"Mechanical Belt", "0",  "0"},
    };

    static final String[][] GENERATORS = {
        {"Water Wheel",       "1",  "256 SU at 8 rpm"},
        {"Large Water Wheel", "—",  "1024 SU at 8 rpm"},
        {"Windmill Bearing",  "—",  "512 SU at 16 rpm"},
        {"Steam Engine",      "—",  "1024 SU at 16 rpm"},
    };

    static void drawPlanner(DrawContext gfx, TextRenderer font) {
        double x = cx();
        double listW = cw() * 0.50;
        double y0 = bodyTop() + 10;
        double rowH = 16;

        for (int i = 0; i < MACHINES.length; i++) {
            double y = y0 + i * rowH;
            int count = Integer.parseInt(MACHINES[i][2]);
            if (i == 2) {
                Render2D.roundedRect(gfx, x - 6, y - 1, listW + 8, rowH, Theme.radiusSmall(),
                        Theme.surfaceHover());
            }
            double ty = y + (rowH - font.fontHeight) / 2.0 - 1;
            Render2D.text(gfx, font, MACHINES[i][0], x, ty,
                    count > 0 ? Theme.textPrimary() : Theme.textSecondary());
            Render2D.textRight(gfx, font, MACHINES[i][1] + " /rpm", x + listW - 26, ty,
                    Theme.textMuted(), false);
            if (count > 0) {
                Render2D.textRight(gfx, font, "x" + count, x + listW, ty, Theme.accent(), false);
            }
        }

        double sx = x + listW + 20;
        double sw = cw() - listW - 20;
        double sy = y0;

        Render2D.text(gfx, font, "Plan", sx, sy, ColorUtil.withAlpha(Theme.accent(), 235));
        sy += font.fontHeight + 7;
        keyValue(gfx, font, "machines", "6", sx, sx + sw, sy);
        sy += font.fontHeight + 3;
        keyValue(gfx, font, "impact", "32 su/rpm", sx, sx + sw, sy);
        sy += font.fontHeight + 3;
        keyValue(gfx, font, "at 64 rpm", "2048 SU", sx, sx + sw, sy);
        sy += font.fontHeight + 14;

        Render2D.text(gfx, font, "Generators needed", sx, sy,
                ColorUtil.withAlpha(Theme.accent(), 235));
        sy += font.fontHeight + 7;

        for (String[] g : GENERATORS) {
            Render2D.text(gfx, font, g[0], sx, sy, Theme.textSecondary());
            Render2D.textRight(gfx, font, g[1].equals("—") ? "—" : "x" + g[1], sx + sw, sy,
                    g[1].equals("—") ? Theme.textMuted() : Theme.textPrimary(), false);
            sy += font.fontHeight + 1;
            Render2D.text(gfx, font, g[2], sx + 8, sy, Theme.textMuted());
            sy += font.fontHeight + 5;
        }
    }

    static void keyValue(DrawContext gfx, TextRenderer font, String k, String v,
                         double x, double right, double y) {
        Render2D.text(gfx, font, k, x, y, Theme.textSecondary());
        Render2D.textRight(gfx, font, v, right, y, Theme.textPrimary(), false);
    }

    static void drawHint(DrawContext gfx, TextRenderer font) {
        double y = py + PANEL_H - HINT_H;
        Render2D.rect(gfx, cx(), y, cw(), 1, ColorUtil.withAlpha(Theme.glassEdge(), 0x99));
        double ty = y + (HINT_H - font.fontHeight) / 2.0 + 1;
        Render2D.text(gfx, font, "click add    right-click remove", cx(), ty, Theme.textMuted());
        Render2D.textRight(gfx, font, "live values", px + PANEL_W - PAD, ty, Theme.textMuted(), false);
    }
}
