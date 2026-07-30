package com.gadzo.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;

/**
 * Thin accessors for the pieces of the game the client touches most.
 *
 * <p>Minecraft 26 moved a few of these behind new owners — the active screen now lives on
 * {@code Gui} rather than on {@code Minecraft}, for instance. Funnelling access through here
 * keeps that knowledge in one file instead of scattered across every screen and HUD element.
 */
public final class Mc {

    private Mc() {
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }

    public static LocalPlayer player() {
        Minecraft client = client();
        return client == null ? null : client.player;
    }

    public static Font font() {
        Minecraft client = client();
        return client == null ? null : client.font;
    }

    /** The screen currently open, or {@code null} when the player has none. */
    public static Screen screen() {
        Minecraft client = client();
        return client == null || client.gui == null ? null : client.gui.screen();
    }

    public static void openScreen(Screen screen) {
        Minecraft client = client();
        if (client != null) {
            client.setScreenAndShow(screen);
        }
    }

    public static void closeScreen() {
        openScreen(null);
    }

    public static boolean inGame() {
        Minecraft client = client();
        return client != null && client.player != null && client.level != null;
    }

    public static int guiWidth() {
        Minecraft client = client();
        return client == null ? 0 : client.getWindow().getGuiScaledWidth();
    }

    public static int guiHeight() {
        Minecraft client = client();
        return client == null ? 0 : client.getWindow().getGuiScaledHeight();
    }

    /** Round-trip latency to the current server, or 0 when it is not known. */
    public static int ping() {
        Minecraft client = client();
        if (client == null || client.player == null || client.getConnection() == null) {
            return 0;
        }
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
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
