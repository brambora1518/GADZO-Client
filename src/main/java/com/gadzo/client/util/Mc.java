package com.gadzo.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Thin accessors for the pieces of the game the client touches most.
 *
 * <p>MinecraftClient 26 moved a few of these behind new owners — the active screen now lives on
 * {@code Gui} rather than on {@code MinecraftClient}, for instance. Funnelling access through here
 * keeps that knowledge in one file instead of scattered across every screen and HUD element.
 */
public final class Mc {

    private Mc() {
    }

    public static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    public static ClientPlayerEntity player() {
        MinecraftClient client = client();
        return client == null ? null : client.player;
    }

    public static TextRenderer font() {
        MinecraftClient client = client();
        return client == null ? null : client.textRenderer;
    }

    /** The screen currently open, or {@code null} when the player has none. */
    public static Screen screen() {
        MinecraftClient client = client();
        return client == null ? null : client.currentScreen;
    }

    public static void openScreen(Screen screen) {
        MinecraftClient client = client();
        if (client != null) {
            client.setScreen(screen);
        }
    }

    public static void closeScreen() {
        openScreen(null);
    }

    public static boolean inGame() {
        MinecraftClient client = client();
        return client != null && client.player != null && client.world != null;
    }

    public static int guiWidth() {
        MinecraftClient client = client();
        return client == null ? 0 : client.getWindow().getScaledWidth();
    }

    public static int guiHeight() {
        MinecraftClient client = client();
        return client == null ? 0 : client.getWindow().getScaledHeight();
    }

    /** Round-trip latency to the current server, or 0 when it is not known. */
    public static int ping() {
        MinecraftClient client = client();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return 0;
        }
        PlayerListEntry info = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    /** Heap in use, in mebibytes. */
    public static long usedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
    }

    public static long maxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    /** Fraction of the heap in use, in {@code [0, 1]}. */
    public static double memoryFraction() {
        long max = maxMemoryMb();
        return max <= 0 ? 0.0 : MathUtil.clamp(usedMemoryMb() / (double) max, 0.0, 1.0);
    }
}
