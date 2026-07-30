package com.gadzo.client.mixin;

import com.gadzo.client.GadzoClient;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes key presses to module keybinds.
 *
 * <p>Hooked here rather than through {@code KeyBinding} so a binding can use any key without
 * colliding with vanilla's controls list, and so presses are seen even when no screen is open.
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void gadzo$handleKeybinds(long window, int key, int scancode, int action,
                                      int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Only act in-world with no screen focused, or a keybind would fire while the player
        // is typing in chat or on a sign.
        if (client == null || client.player == null || client.currentScreen != null) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            GadzoClient.onKeyPressed(key);
        } else if (action == GLFW.GLFW_RELEASE) {
            GadzoClient.onKeyReleased(key);
        }
    }
}
