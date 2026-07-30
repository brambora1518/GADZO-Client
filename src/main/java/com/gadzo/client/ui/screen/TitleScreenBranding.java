package com.gadzo.client.ui.screen;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.system.SystemProfile;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * GADZO branding on the vanilla title screen.
 *
 * <p>Drawn as an overlay through Fabric's screen events rather than by replacing
 * {@code TitleScreen}. Substituting the whole screen would mean re-implementing world
 * loading, realms, accessibility onboarding and the multiplayer warning — all of which
 * change between versions — for a purely cosmetic gain. An overlay keeps every vanilla
 * button working and cannot break the path into a world.
 */
public final class TitleScreenBranding {

    private static final double PANEL_WIDTH = 148.0;
    private static final double PANEL_HEIGHT = 46.0;
    private static final double MARGIN = 8.0;

    private TitleScreenBranding() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen)) {
                return;
            }
            ScreenEvents.afterExtract(screen).register(TitleScreenBranding::draw);
        });
    }

    private static void draw(net.minecraft.client.gui.screens.Screen screen, GuiGraphicsExtractor gfx,
                             int mouseX, int mouseY, float partialTick) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        double x = MARGIN;
        double y = screen.height - PANEL_HEIGHT - MARGIN;

        Render2D.shadow(gfx, x, y, PANEL_WIDTH, PANEL_HEIGHT, Theme.radiusSmall(), 5,
                Theme.shadowColor());
        Render2D.roundedRect(gfx, x, y, PANEL_WIDTH, PANEL_HEIGHT, Theme.radiusSmall(),
                ColorUtil.withAlpha(Theme.surface(), 225));

        // Accent bar down the leading edge, matching the toast styling.
        Render2D.roundedRect(gfx, x, y, 3, PANEL_HEIGHT,
                Theme.radiusSmall(), 0, 0, Theme.radiusSmall(), Theme.accent());

        double textX = x + 11;
        Render2D.text(gfx, font, "GADZO", textX, y + 7, Theme.accent());
        Render2D.text(gfx, font, "CLIENT", textX + font.width("GADZO") + 4, y + 7, Theme.textMuted());

        Render2D.text(gfx, font, "v" + GadzoClient.VERSION + "  ·  Minecraft 26.2",
                textX, y + 19, Theme.textSecondary());

        // Tier is only meaningful once the CPU benchmark has landed; before that it would
        // read "Medium" for everyone, which is worse than saying nothing.
        String status = com.gadzo.client.core.system.CpuBenchmark.isReady()
                ? SystemProfile.detectTier() + " tier  ·  " + GadzoClient.modules().enabledCount() + " on"
                : GadzoClient.modules().enabledCount() + " modules on";
        Render2D.text(gfx, font, status, textX, y + 31, Theme.textMuted());
    }
}
