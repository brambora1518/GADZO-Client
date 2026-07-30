package com.gadzo.client.modules.visual;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.util.Animation;
import com.gadzo.client.util.Easing;

import org.lwjgl.glfw.GLFW;

/**
 * Optifine-style zoom.
 *
 * <p>The module holds the zoom state and the smoothing; a mixin on the FOV calculation reads
 * {@link #currentFovMultiplier()} and scales the field of view by it. Keeping the maths here
 * rather than in the mixin means the smoothing curve is configurable like any other setting.
 */
public class Zoom extends Module {

    private static Zoom instance;

    private final NumberSetting zoomFactor;
    private final BooleanSetting smooth;
    private final NumberSetting smoothSpeed;
    private final BooleanSetting reduceSensitivity;

    private final Animation zoomAnimation = new Animation(1.0, 180L, Easing.EXPO_OUT);

    /** Set while the zoom key is held. */
    private boolean zooming;

    public Zoom() {
        super("Zoom", "Hold a key to zoom the camera in", ModuleCategory.VISUAL);
        instance = this;
        // Zoom is driven by holding its keybind, so the module itself stays enabled.
        markPermanent();
        // C is where every other client puts zoom, and vanilla leaves it free.
        getKeybind().setValue(GLFW.GLFW_KEY_C);

        this.zoomFactor = addNumber("Zoom level", 4.0, 1.5, 12.0, 0.5,
                "How far in the camera zooms");
        this.smooth = addBool("Smooth", true, "Ease into the zoom instead of snapping");
        this.smoothSpeed = add(new NumberSetting("Smoothing", 180, 40, 600, 20)
                .suffix(" ms")
                .<NumberSetting>describe("Time the zoom takes to reach full")
                .visibleWhen(() -> this.smooth.get()));
        this.reduceSensitivity = addBool("Reduce sensitivity", true,
                "Scale mouse sensitivity down while zoomed, so aiming stays precise");
    }

    public static Zoom get() {
        return instance;
    }

    @Override
    public void onKeybindPressed() {
        setZooming(true);
    }

    @Override
    public void onKeybindReleased() {
        setZooming(false);
    }

    /** Called from the key handler while the zoom key is held or released. */
    public void setZooming(boolean value) {
        if (this.zooming == value) {
            return;
        }
        this.zooming = value;
        zoomAnimation.to(value ? 1.0 / zoomFactor.get() : 1.0);
        if (!smooth.get()) {
            zoomAnimation.snapTo(value ? 1.0 / zoomFactor.get() : 1.0);
        }
    }

    public boolean isZooming() {
        return zooming;
    }

    /**
     * Multiplier to apply to the field of view.
     *
     * <p>1.0 means no zoom; smaller values narrow the FOV and therefore zoom in.
     */
    public double currentFovMultiplier() {
        return zoomAnimation.value();
    }

    public boolean isActive() {
        return zooming || zoomAnimation.value() < 0.999;
    }

    /** Sensitivity scale to apply while zoomed, or 1.0 when the option is off. */
    public double sensitivityMultiplier() {
        if (!reduceSensitivity.get()) {
            return 1.0;
        }
        // Track the zoom itself so sensitivity eases in alongside the FOV.
        return currentFovMultiplier();
    }

    public long smoothingMillis() {
        return smoothSpeed.getInt();
    }
}
