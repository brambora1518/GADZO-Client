package com.gadzo.client.ui;

import com.gadzo.client.GadzoClient;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.io.IOException;

/**
 * Real frosted-glass blur behind GADZO's panels, on a version that has no GUI blur API.
 *
 * <p>Minecraft 1.20.1 gained no backdrop blur of its own — that arrived with the render-state
 * rework several versions later. What it does ship, unused by any vanilla screen, is
 * {@code assets/minecraft/shaders/post/blur.json}: a two-pass separable Gaussian that reads the
 * main framebuffer and writes straight back to it. Pointing {@link PostEffectProcessor} at that
 * file gives the effect on this version too, which is how the long-standing standalone blur
 * mods for 1.20.1 have always done it.
 *
 * <p>Order is what makes it work rather than any cleverness. A screen's {@code render} runs
 * after the world has already been drawn into the main framebuffer, so blurring it at the top
 * of that method blurs the world and nothing else; the panel is then drawn, sharp, on top of
 * the blurred result.
 *
 * <p>Everything here fails soft. A missing shader, a driver that rejects the program, a
 * framebuffer that is not ready — any of them latches the whole thing off after one logged
 * line, and the screens fall back to the plain dim they used before. A cosmetic effect has no
 * business taking the game down with it.
 */
public final class BackgroundBlur {

    /** Vanilla's own post-processing blur, present in every 1.20.1 install. */
    private static final Identifier BLUR_SHADER = new Identifier("minecraft", "shaders/post/blur.json");

    private static PostEffectProcessor processor;

    /** Framebuffer size the processor was last built for; a resize invalidates it. */
    private static int builtForWidth = -1;
    private static int builtForHeight = -1;

    /** Latched on the first failure so a broken shader logs once, not once per frame. */
    private static boolean unavailable;

    private BackgroundBlur() {
    }

    /**
     * Blurs whatever has already been drawn this frame.
     *
     * @return whether the blur actually ran; {@code false} means callers should dim instead
     */
    public static boolean apply(DrawContext gfx) {
        if (unavailable) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        Framebuffer target = client.getFramebuffer();
        if (target == null || !ensureProcessor(client, target)) {
            return false;
        }

        try {
            // Flush the GUI batch first. Anything still queued has not reached the framebuffer
            // yet, and the blur reads the framebuffer — without this, whatever vanilla had
            // pending would be composited after the blur and come out sharp.
            gfx.draw();

            processor.render(client.getLastFrameDuration());

            // The post pass leaves its own framebuffer bound and its own blend state set.
            // Hand both back in the shape the GUI expects, or every panel drawn after this
            // point inherits it.
            target.beginWrite(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return true;
        } catch (RuntimeException e) {
            unavailable = true;
            GadzoClient.LOGGER.warn("Backdrop blur failed and has been disabled for this "
                    + "session; panels will dim instead. {}", e.toString());
            return false;
        }
    }

    /**
     * Builds the processor, or rebuilds it after a resize.
     *
     * <p>The post effect allocates its own intermediate target at the framebuffer's size, so
     * it cannot simply be kept across a window resize — it has to be torn down and remade.
     */
    private static boolean ensureProcessor(MinecraftClient client, Framebuffer target) {
        int width = target.textureWidth;
        int height = target.textureHeight;
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (processor != null && width == builtForWidth && height == builtForHeight) {
            return true;
        }
        try {
            if (processor != null) {
                processor.close();
                processor = null;
            }
            processor = new PostEffectProcessor(client.getTextureManager(),
                    client.getResourceManager(), target, BLUR_SHADER);
            processor.setupDimensions(width, height);
            builtForWidth = width;
            builtForHeight = height;
            return true;
        } catch (IOException | RuntimeException e) {
            unavailable = true;
            GadzoClient.LOGGER.warn("Could not load the backdrop blur shader; panels will dim "
                    + "instead. {}", e.toString());
            return false;
        }
    }

    /** Drops the processor, e.g. on resource reload. It is rebuilt lazily on next use. */
    public static void invalidate() {
        if (processor != null) {
            try {
                processor.close();
            } catch (RuntimeException ignored) {
                // Closing a already-broken processor is not worth reporting.
            }
            processor = null;
        }
        builtForWidth = -1;
        builtForHeight = -1;
    }
}
