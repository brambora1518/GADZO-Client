package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.EnumSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Real-world clock and/or in-game time. */
public class ClockHud extends HudModule {

    /** Which clock to display. */
    public enum Mode {
        REAL_24("Real time (24h)"),
        REAL_12("Real time (12h)"),
        IN_GAME("In-game day"),
        BOTH("Both");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final DateTimeFormatter FORMAT_24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMAT_12 = DateTimeFormatter.ofPattern("h:mm a");

    /** MinecraftClient ticks per in-game day. */
    private static final long TICKS_PER_DAY = 24000L;

    private final EnumSetting<Mode> mode;

    public ClockHud() {
        super("Clock", "Real or in-game time", HudAnchor.TOP_RIGHT, -4, 34);
        this.mode = addEnum("Mode", Mode.REAL_24, "Which clock to show");
    }

    private String text() {
        return switch (mode.get()) {
            case REAL_24 -> LocalTime.now().format(FORMAT_24);
            case REAL_12 -> LocalTime.now().format(FORMAT_12);
            case IN_GAME -> inGameTime();
            case BOTH -> LocalTime.now().format(FORMAT_24) + "  |  " + inGameTime();
        };
    }

    /**
     * In-game time of day as a 24-hour clock.
     *
     * <p>MinecraftClient's tick 0 is 06:00, so the offset is folded in before converting.
     */
    private String inGameTime() {
        if (Mc.client() == null || Mc.client().world == null) {
            return "--:--";
        }
        long timeOfDay = Math.floorMod(Mc.client().world.getTimeOfDay() + 6000L, TICKS_PER_DAY);
        long hours = timeOfDay / 1000L;
        long minutes = (timeOfDay % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public double contentWidth(TextRenderer font) {
        return font.getWidth(text());
    }

    @Override
    public double contentHeight(TextRenderer font) {
        return font.fontHeight;
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        line(gfx, font, text(), 0, 0, Theme.textPrimary());
    }
}
