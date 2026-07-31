import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/** Renders a faithful mock of the mods menu with the real Render2D and Theme. */
public class Preview {

    static final int W = 900, H = 560;
    static final double WINDOW_WIDTH = 620, WINDOW_HEIGHT = 380;
    static final double SIDEBAR_WIDTH = 132, HEADER_HEIGHT = 46, FOOTER_HEIGHT = 26;
    static final double MODULE_ROW_HEIGHT = 34, PADDING = 10, LIST_FRACTION = 0.48;
    static final double TOGGLE_W = 26, TOGGLE_H = 14;

    static double windowX, windowY;

    public static void main(String[] args) throws Exception {
        boolean blurred = args.length > 0 && args[0].equals("blur");
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        drawWorld(img, blurred);
        DrawContext gfx = new DrawContext(img);
        TextRenderer font = new TextRenderer();

        // Backdrop dim, matching ClickGuiScreen.drawBackdrop.
        Render2D.rect(gfx, 0, 0, W, H, blurred ? 0x38000000 : 0xB0000000);

        windowX = (W - WINDOW_WIDTH) / 2.0;
        windowY = (H - WINDOW_HEIGHT) / 2.0;

        Render2D.shadow(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(), 10, Theme.shadowColor());
        Render2D.roundedRect(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(), Theme.background());

        drawSidebar(gfx, font);
        drawHeader(gfx, font);
        drawModuleList(gfx, font);
        drawSettings(gfx, font);
        drawFooter(gfx, font);

        Render2D.roundedOutline(gfx, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, Theme.radius(), 1.0, Theme.glassEdge());

        ImageIO.write(img, "png", new File(args.length > 1 ? args[1] : "preview.png"));
    }

    static double contentX() { return windowX + SIDEBAR_WIDTH; }
    static double contentWidth() { return WINDOW_WIDTH - SIDEBAR_WIDTH; }
    static double listWidth() { return contentWidth() * LIST_FRACTION; }
    static double listTop() { return windowY + HEADER_HEIGHT; }
    static double listBottom() { return windowY + WINDOW_HEIGHT - FOOTER_HEIGHT; }
    static double settingsX() { return contentX() + listWidth(); }
    static double settingsWidth() { return contentWidth() - listWidth(); }

    /** A blocky pseudo-Minecraft backdrop, optionally pre-blurred to simulate the shader. */
    static void drawWorld(BufferedImage img, boolean blur) {
        Random rnd = new Random(7);
        int[][] palette = {{104,158,68},{124,178,80},{88,138,58},{132,116,92},{110,96,76},{150,200,240},{120,170,220}};
        for (int by = 0; by < H; by += 18) {
            for (int bx = 0; bx < W; bx += 18) {
                int[] c = palette[by < H * 0.42 ? 5 + rnd.nextInt(2) : rnd.nextInt(5)];
                int j = rnd.nextInt(22) - 11;
                int col = 0xFF000000 | (cl(c[0]+j) << 16) | (cl(c[1]+j) << 8) | cl(c[2]+j);
                for (int y = by; y < Math.min(by + 18, H); y++)
                    for (int x = bx; x < Math.min(bx + 18, W); x++) img.setRGB(x, y, col);
            }
        }
        if (blur) boxBlur(img, 20);
    }
    static int cl(int v) { return Math.max(0, Math.min(255, v)); }

    /** Separable box blur, standing in for the two-pass Gaussian the shader runs. */
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
            int[] dst2 = new int[src.length];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                long sr=0,sg=0,sb=0; int n=0;
                for (int k=-r;k<=r;k++){int yy=y+k; if(yy<0||yy>=H)continue; int c=dst[yy*W+x];
                    sr+=(c>>16)&0xFF; sg+=(c>>8)&0xFF; sb+=c&0xFF; n++;}
                dst2[y*W+x]=0xFF000000|((int)(sr/n)<<16)|((int)(sg/n)<<8)|(int)(sb/n);
            }
            img.setRGB(0, 0, W, H, dst2, 0, W);
        }
    }

    static final String[] CATEGORIES = {"Performance","HUD","Visual","Combat","Movement","Survival","Create","Client"};
    static final int[] COUNTS = {5,5,1,5,3,4,1,4};

    static void drawSidebar(DrawContext gfx, TextRenderer font) {
        Render2D.roundedRect(gfx, windowX, windowY, SIDEBAR_WIDTH, WINDOW_HEIGHT,
                Theme.radius(), 0, 0, Theme.radius(), Theme.surface());
        Render2D.text(gfx, font, "GADZO", windowX + PADDING + 2, windowY + 15, Theme.accent());
        Render2D.text(gfx, font, "CLIENT", windowX + PADDING + 2 + font.getWidth("GADZO") + 5, windowY + 15, Theme.textMuted());

        double y = windowY + HEADER_HEIGHT;
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean selected = i == 0;
            boolean hovered = i == 2;
            if (selected || hovered) {
                Render2D.roundedRect(gfx, windowX + 6, y, SIDEBAR_WIDTH - 12, 28, Theme.radiusSmall(),
                        selected ? ColorUtil.withAlpha(Theme.accent(), 42) : Theme.surfaceHover());
            }
            if (selected) Render2D.roundedRect(gfx, windowX + 6, y + 7, 3, 14, 1.5, Theme.accent());
            Render2D.text(gfx, font, CATEGORIES[i], windowX + 18, y + 10,
                    selected ? Theme.textPrimary() : Theme.textSecondary());
            Render2D.textRight(gfx, font, String.valueOf(COUNTS[i]), windowX + SIDEBAR_WIDTH - 14, y + 10, Theme.textMuted(), false);
            y += 30;
        }
    }

    static void drawHeader(DrawContext gfx, TextRenderer font) {
        double x = contentX() + PADDING, y = windowY + 13;
        double boxWidth = contentWidth() - PADDING * 2;
        Render2D.roundedRect(gfx, x, y, boxWidth, 20, Theme.radiusSmall(), ColorUtil.fade(Theme.surfaceHigh(), 0.7));
        Render2D.text(gfx, font, "Search modules...", x + 7, y + 6, Theme.textMuted());
    }

    static final String[][] MODULES = {
        {"Render tuning","Render distance, culling budget..."},
        {"Dynamic FPS","Lower the frame cap when the ..."},
        {"Entity culling","Stop drawing entities you canno..."},
        {"Particle limiter","Bound how many particles can s..."},
        {"Screen effects","Reduce nausea, portal and dar..."},
        {"Weather render","Skip drawing rain and snow"},
        {"Frametime graph","Frame time history and 1% lows"},
    };
    static final boolean[] ENABLED = {true,false,true,false,false,false,true};

    static void drawModuleList(DrawContext gfx, TextRenderer font) {
        double x = contentX() + PADDING;
        double width = listWidth() - PADDING * 1.5;
        double y = listTop() + 6;
        for (int i = 0; i < MODULES.length; i++) {
            boolean selected = i == 0;
            boolean hovered = i == 3;
            double t = ENABLED[i] ? 1.0 : 0.0;
            if (selected || hovered) {
                int tint = selected ? ColorUtil.withAlpha(Theme.accent(), 30) : Theme.surfaceHover();
                Render2D.roundedRect(gfx, x, y, width, MODULE_ROW_HEIGHT, Theme.radiusSmall(), tint);
            }
            double barAlpha = Math.max(t, selected ? 0.5 : 0.0);
            if (barAlpha > 0.01)
                Render2D.roundedRect(gfx, x, y + 6, 3, MODULE_ROW_HEIGHT - 12, 1.5, ColorUtil.fade(Theme.accent(), barAlpha));
            int nameW = (int)(width - 52);
            Render2D.text(gfx, font, Render2D.truncate(font, MODULES[i][0], nameW), x + 11, y + 6, Theme.textPrimary());
            Render2D.text(gfx, font, Render2D.truncate(font, MODULES[i][1], nameW), x + 11, y + 18, Theme.textMuted());
            drawToggle(gfx, x + width - 36, y + (MODULE_ROW_HEIGHT - TOGGLE_H) / 2.0, t);
            y += MODULE_ROW_HEIGHT + 4;
        }
    }

    static void drawToggle(DrawContext gfx, double x, double y, double t) {
        int track = ColorUtil.mix(Theme.trackOff(), ColorUtil.withAlpha(Theme.accent(),184), t);
        Render2D.roundedRect(gfx, x, y, TOGGLE_W, TOGGLE_H, TOGGLE_H / 2.0, track);
        double knob = TOGGLE_H - 4;
        Render2D.roundedRect(gfx, x + 2 + (TOGGLE_W - TOGGLE_H) * t, y + 2, knob, knob, knob / 2.0, ColorUtil.mix(0xFFCFD6E4,0xFFFFFFFF,t));
    }

    static void drawSettings(DrawContext gfx, TextRenderer font) {
        double x = settingsX(), width = settingsWidth();
        double top = listTop(), bottom = listBottom();
        double innerX = x + PADDING, innerWidth = width - PADDING * 2;
        double y = top + 8;
        Render2D.text(gfx, font, "Render tuning", innerX, y, Theme.textPrimary());
        y += font.fontHeight + 2;
        Render2D.text(gfx, font, "Render distance, culling budget", innerX, y, Theme.textMuted());
        y += font.fontHeight + 8;
        Render2D.separator(gfx, innerX, y, innerWidth, Theme.border());
        y += 10;

        drawSettingToggle(gfx, font, "Ambient occlusion", innerX, y, innerWidth, true); y += 28;
        drawSettingToggle(gfx, font, "Entity shadows", innerX, y, innerWidth, false); y += 28;
        drawSettingSlider(gfx, font, "Render distance", "8", innerX, y, innerWidth, 0.42); y += 28;
        drawSettingSlider(gfx, font, "Mipmap levels", "4", innerX, y, innerWidth, 1.0); y += 28;
        drawSettingSwatch(gfx, font, "Accent", innerX, y, innerWidth, Theme.accent());
    }

    static void drawSettingToggle(DrawContext gfx, TextRenderer font, String name, double x, double y, double w, boolean on) {
        Render2D.text(gfx, font, name, x, y + 7, Theme.textSecondary());
        double tw = 26, th = 14;
        double t = on ? 1 : 0;
        int track = ColorUtil.mix(Theme.trackOff(), ColorUtil.withAlpha(Theme.accent(),184), t);
        Render2D.roundedRect(gfx, x + w - tw, y + 5, tw, th, th / 2.0, track);
        double knob = th - 4;
        Render2D.roundedRect(gfx, x + w - tw + 2 + (tw - th) * t, y + 7, knob, knob, knob / 2.0, ColorUtil.mix(0xFFCFD6E4,0xFFFFFFFF,t));
    }

    static void drawSettingSlider(DrawContext gfx, TextRenderer font, String name, String val, double x, double y, double w, double f) {
        Render2D.text(gfx, font, name, x, y + 3, Theme.textSecondary());
        Render2D.textRight(gfx, font, val, x + w, y + 3, Theme.textPrimary(), false);
        double trackY = y + 28 - 9;
        Render2D.roundedRect(gfx, x, trackY, w, 3, 1.5, Theme.trackOff());
        if (w * f > 0.5) Render2D.roundedRect(gfx, x, trackY, w * f, 3, 1.5, ColorUtil.withAlpha(Theme.accent(),200));
        Render2D.roundedRect(gfx, x + w * f - 4.5, trackY + 1.5 - 4.5, 9, 9, 4.5, 0xFFF4F6FA);
    }

    static void drawSettingSwatch(DrawContext gfx, TextRenderer font, String name, double x, double y, double w, int color) {
        Render2D.text(gfx, font, name, x, y + 7, Theme.textSecondary());
        Render2D.roundedRect(gfx, x + w - 14, y + 5, 14, 14, Theme.radiusSmall(), color);
    }

    static void drawFooter(DrawContext gfx, TextRenderer font) {
        double y = windowY + WINDOW_HEIGHT - FOOTER_HEIGHT;
        Render2D.separator(gfx, contentX(), y, contentWidth(), Theme.border());
        Render2D.text(gfx, font, "Medium tier  ·  8 threads", contentX() + PADDING, y + 9, Theme.textMuted());
        Render2D.textRight(gfx, font, "v1.0.0", windowX + WINDOW_WIDTH - PADDING, y + 9, Theme.textMuted(), false);
    }
}
