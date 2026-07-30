package com.gadzo.client.mixin;

import com.gadzo.client.GadzoClient;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes key presses to module keybinds.
 *
 * <p>Hooked here rather than through {@code KeyMapping} so a binding can use any key without
 * colliding with vanilla's controls list, and so presses are seen even when no screen is
 * open.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void gadzo$handleKeybinds(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        // Only act while the player is in the world with no screen focused, otherwise a
        // keybind would fire while the user is typing in chat or a sign.
        if (client == null || client.player == null || client.gui.screen() != null) {
            return;
        }

        if (action == GLFW.GLFW_PRESS) {
            GadzoClient.onKeyPressed(event.key());
        } else if (action == GLFW.GLFW_RELEASE) {
            GadzoClient.onKeyReleased(event.key());
        }
    }
}
