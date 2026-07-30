package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Biome, in-game day number, and the server you are connected to. */
public class WorldInfoHud extends HudModule {

    /** Ticks in one Minecraft day. */
    private static final long TICKS_PER_DAY = 24000L;

    private final BooleanSetting showBiome;
    private final BooleanSetting showDay;
    private final BooleanSetting showServer;

    public WorldInfoHud() {
        super("World info", "Biome, day number and server", HudAnchor.TOP_RIGHT, -4, 48);
        this.showBiome = addBool("Biome", true, "Name of the biome you are standing in");
        this.showDay = addBool("Day", true, "In-game day number");
        this.showServer = addBool("Server", true, "Address of the server, or Singleplayer");
    }

    /** Turns {@code minecraft:snowy_taiga} into {@code Snowy Taiga}. */
    private static String prettify(String path) {
        String[] words = path.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    private String biomeName() {
        LocalPlayer player = Mc.player();
        Minecraft client = Mc.client();
        if (player == null || client == null || client.level == null) {
            return null;
        }
        Holder<Biome> biome = client.level.getBiome(player.blockPosition());
        // Unregistered biomes (datapacks mid-reload) have no key; skip rather than show junk.
        return biome.unwrapKey()
                .map(key -> prettify(key.identifier().getPath()))
                .orElse(null);
    }

    private String serverName() {
        Minecraft client = Mc.client();
        if (client == null) {
            return null;
        }
        if (client.hasSingleplayerServer()) {
            return "Singleplayer";
        }
        ServerData server = client.getCurrentServer();
        return server == null ? null : server.ip;
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(3);
        Minecraft client = Mc.client();

        if (showBiome.get()) {
            String biome = biomeName();
            if (biome != null) {
                lines.add(biome);
            }
        }
        if (showDay.get() && client != null && client.level != null) {
            lines.add("Day " + (client.level.getDefaultClockTime() / TICKS_PER_DAY));
        }
        if (showServer.get()) {
            String server = serverName();
            if (server != null) {
                lines.add(server);
            }
        }
        return lines;
    }

    @Override
    public double contentWidth(Font font) {
        double widest = 0;
        for (String line : lines()) {
            widest = Math.max(widest, font.width(line));
        }
        return widest;
    }

    @Override
    public double contentHeight(Font font) {
        return Math.max(0, lines().size()) * font.lineHeight;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor gfx, Font font) {
        double y = 0;
        for (String text : lines()) {
            line(gfx, font, text, 0, y, Theme.textPrimary());
            y += font.lineHeight;
        }
    }
}
