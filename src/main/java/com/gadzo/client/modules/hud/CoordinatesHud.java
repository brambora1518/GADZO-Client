package com.gadzo.client.modules.hud;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Player position, facing, and optionally the Nether/Overworld coordinate conversion. */
public class CoordinatesHud extends HudModule {

    private final BooleanSetting showFacing;
    private final BooleanSetting showDimensionConversion;
    private final BooleanSetting singleLine;

    public CoordinatesHud() {
        super("Coordinates", "Player position and facing", HudAnchor.TOP_LEFT, 4, 32);
        this.showFacing = addBool("Facing", true, "Show which way you are looking");
        this.showDimensionConversion = addBool("Nether conversion", false,
                "Show the matching coordinates in the other dimension");
        this.singleLine = addBool("Single line", false, "Pack X/Y/Z onto one row");
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(3);
        LocalPlayer player = Mc.player();
        if (player == null) {
            return lines;
        }

        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());

        if (singleLine.get()) {
            lines.add("XYZ: " + x + " " + y + " " + z);
        } else {
            lines.add("X: " + x);
            lines.add("Y: " + y);
            lines.add("Z: " + z);
        }

        if (showDimensionConversion.get()) {
            // The Nether is 8 blocks of Overworld per block; which way to scale depends on
            // where the player currently is.
            boolean inNether = player.level().dimension() == Level.NETHER;
            int convertedX = inNether ? x * 8 : x / 8;
            int convertedZ = inNether ? z * 8 : z / 8;
            lines.add((inNether ? "OW: " : "NE: ") + convertedX + " " + convertedZ);
        }

        if (showFacing.get()) {
            Direction facing = player.getDirection();
            lines.add("Facing: " + capitalise(facing.getName()) + " " + axisHint(facing));
        }

        return lines;
    }

    /** The classic "which way do X and Z go" reminder shown next to the facing. */
    private static String axisHint(Direction facing) {
        return switch (facing) {
            case NORTH -> "(-Z)";
            case SOUTH -> "(+Z)";
            case WEST -> "(-X)";
            case EAST -> "(+X)";
            default -> "";
        };
    }

    private static String capitalise(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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
        return Math.max(1, lines().size()) * font.lineHeight;
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
