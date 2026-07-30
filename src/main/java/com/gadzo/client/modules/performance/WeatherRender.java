package com.gadzo.client.modules.performance;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;

/**
 * Skips rendering rain and snow.
 *
 * <p>Weather is drawn as a large number of camera-facing quads every frame and is one of the
 * few effects that reliably halves the frame rate on older hardware during a storm. Skipping
 * the draw changes nothing about the weather itself — the sky still darkens, mobs still spawn,
 * crops still grow — so this is purely a rendering cost being declined.
 */
public class WeatherRender extends Module {

    private static WeatherRender instance;

    private final BooleanSetting hideRain;
    private final BooleanSetting hideStars;

    public WeatherRender() {
        super("Weather render", "Skip drawing rain, snow and stars", ModuleCategory.PERFORMANCE);
        instance = this;
        this.hideRain = addBool("Hide rain and snow", true,
                "Stop drawing precipitation; the weather itself is unaffected");
        this.hideStars = addBool("Hide sun, moon and stars", false,
                "Skip the celestial bodies as well");
    }

    public static WeatherRender get() {
        return instance;
    }

    /** Consulted by the weather renderer mixin. */
    public boolean shouldSkipPrecipitation() {
        return isEnabled() && hideRain.get();
    }

    /** Consulted by the sky renderer mixin. */
    public boolean shouldSkipCelestials() {
        return isEnabled() && hideStars.get();
    }
}
