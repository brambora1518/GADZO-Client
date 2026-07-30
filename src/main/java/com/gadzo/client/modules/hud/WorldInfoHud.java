package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Biome, in-game day number, and the server you are connected to. */
public class WorldInfoHud extends HudModule {

    /** Ticks in one MinecraftClient day. */
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
        ClientPlayerEntity player = Mc.player();
        MinecraftClient client = Mc.client();
        if (player == null || client == null || client.world == null) {
            return null;
        }
        RegistryEntry<Biome> biome = client.world.getBiome(player.getBlockPos());
        // Unregistered biomes (datapacks mid-reload) have no key; skip rather than show junk.
        return biome.getKey()
                .map(key -> prettify(key.getValue().getPath()))
                .orElse(null);
    }

    private String serverName() {
        MinecraftClient client = Mc.client();
        if (client == null) {
            return null;
        }
        if (client.isInSingleplayer()) {
            return "Singleplayer";
        }
        ServerInfo server = client.getCurrentServerEntry();
        return server == null ? null : server.address;
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(3);
        MinecraftClient client = Mc.client();

        if (showBiome.get()) {
            String biome = biomeName();
            if (biome != null) {
                lines.add(biome);
            }
        }
        if (showDay.get() && client != null && client.world != null) {
            lines.add("Day " + (client.world.getTimeOfDay() / TICKS_PER_DAY));
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
    public double contentWidth(TextRenderer font) {
        double widest = 0;
        for (String line : lines()) {
            widest = Math.max(widest, font.getWidth(line));
        }
        return widest;
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return Math.max(0, lines().size()) * font.fontHeight;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        double y = 0;
        for (String text : lines()) {
            line(gfx, font, text, 0, y, Theme.textPrimary());
            y += font.fontHeight;
        }
    }
}
