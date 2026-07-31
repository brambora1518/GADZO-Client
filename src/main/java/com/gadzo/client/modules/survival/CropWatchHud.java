package com.gadzo.client.modules.survival;

import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.ui.Render2D;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.Mc;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Optional;

/**
 * Is the crop you are looking at ready to harvest.
 *
 * <p>Growth in Minecraft is a block state property, not something with its own UI anywhere —
 * you either know each crop's fully-grown texture by sight or you break it and find out. This
 * reads the property directly instead: any block with an integer property literally named
 * {@code age} gets its current value compared against the property's own maximum, which is
 * exactly the number the game itself uses to decide whether the crop can still grow.
 *
 * <p>Generic on purpose. Wheat tops out at 7, beetroot at 3, and a modded crop could be
 * anything — reading the property's declared range instead of hard-coding per-block numbers
 * means this is correct for all of them, including crops this client has never heard of.
 */
public class CropWatchHud extends HudModule {

    private static final String AGE_PROPERTY_NAME = "age";

    private final BooleanSetting hideWhenNotLooking;

    public CropWatchHud() {
        super("Crop watch", "Growth stage of the block you are looking at",
                ModuleCategory.SURVIVAL, HudAnchor.BOTTOM_LEFT, 6, -160);
        this.hideWhenNotLooking = addBool("Hide otherwise", true,
                "Only appear while looking at something with a growth stage");
    }

    /**
     * Cache for {@link #read()}. {@code render()} reaches {@code contentWidth}/{@code
     * contentHeight}/{@code renderContent} several times each frame (see {@code HudModule}),
     * and re-running the raycast and property scan that many times a frame for a value that
     * cannot change between them is the same mistake the durability readout made.
     */
    private static final long CACHE_MILLIS = 50;
    private Optional<Reading> cached = Optional.empty();
    private long cachedAt;

    private Optional<Reading> read() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MILLIS) {
            return cached;
        }
        cachedAt = now;
        cached = compute();
        return cached;
    }

    /** The block's age property and its current value, or empty if it has none. */
    private Optional<Reading> compute() {
        MinecraftClient client = Mc.client();
        if (client == null || client.world == null
                || !(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);

        for (Property<?> property : state.getEntries().keySet()) {
            if (property.getName().equals(AGE_PROPERTY_NAME) && property instanceof IntProperty age) {
                int current = state.get(age);
                int max = Collections.max(age.getValues());
                String name = state.getBlock().getName().getString();
                return Optional.of(new Reading(name, current, max));
            }
        }
        return Optional.empty();
    }

    private record Reading(String name, int age, int maxAge) {
        boolean ready() {
            return age >= maxAge;
        }
    }

    private boolean visible(Optional<Reading> reading) {
        return reading.isPresent() || !hideWhenNotLooking.get();
    }

    @Override
    public double contentWidth(TextRenderer font) {
        Optional<Reading> reading = read();
        if (!visible(reading)) {
            return 0;
        }
        if (reading.isEmpty()) {
            return font.getWidth("Not looking at a crop");
        }
        return Math.max(font.getWidth(reading.get().name()), font.getWidth("Growing 9 / 9"));
    }

    @Override
    public double contentHeight(TextRenderer font) {
        Optional<Reading> reading = read();
        if (!visible(reading)) {
            return 0;
        }
        // Name row + state row, plus a progress bar row when the crop is not yet ready — the
        // bar is only ever drawn in that case, so reporting its height the rest of the time
        // would leave a blank strip under the background plate.
        boolean needsBarRow = reading.map(r -> !r.ready()).orElse(false);
        return (font.fontHeight + 2) * 2 - 2 + (needsBarRow ? 6 : 0);
    }

    @Override
    protected void renderContent(DrawContext gfx, TextRenderer font) {
        Optional<Reading> found = read();
        if (!visible(found)) {
            return;
        }
        if (found.isEmpty()) {
            line(gfx, font, "Not looking at a crop", 0, 0, Theme.textMuted());
            return;
        }
        Reading reading = found.get();
        double width = contentWidth(font);

        line(gfx, font, reading.name(), 0, 0, Theme.textPrimary());

        String state = reading.ready() ? "Ready to harvest"
                : reading.age() + " / " + reading.maxAge();
        Render2D.text(gfx, font, state, 0, font.fontHeight + 2,
                reading.ready() ? Theme.success() : Theme.warning());

        if (!reading.ready()) {
            double barY = font.fontHeight + 2 + font.fontHeight + 1;
            double barWidth = width;
            double fraction = reading.maxAge() == 0 ? 1.0 : reading.age() / (double) reading.maxAge();
            Render2D.roundedRect(gfx, 0, barY, barWidth, 3, 1.5,
                    com.gadzo.client.util.ColorUtil.withAlpha(Theme.trackOff(), 190));
            double filled = barWidth * fraction;
            if (filled > 0.5) {
                Render2D.roundedRect(gfx, 0, barY, filled, 3, 1.5, Theme.warning());
            }
        }
    }
}
