package com.gadzo.client;

import com.gadzo.client.core.command.GadzoCommands;
import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.hud.HudManager;
import com.gadzo.client.core.module.ModuleManager;
import com.gadzo.client.core.system.CpuBenchmark;
import com.gadzo.client.core.system.SystemProfile;
import com.gadzo.client.modules.client.ClientSettings;
import com.gadzo.client.modules.client.CreateHelperLauncher;
import com.gadzo.client.modules.client.HudEditorLauncher;
import com.gadzo.client.modules.client.MenuLauncher;
import com.gadzo.client.modules.client.SurvivalHelperLauncher;
import com.gadzo.client.modules.create.KineticHud;
import com.gadzo.client.modules.create.StressAlert;
import com.gadzo.client.modules.hud.ArmorHud;
import com.gadzo.client.modules.hud.ClockHud;
import com.gadzo.client.modules.hud.ComboHud;
import com.gadzo.client.modules.hud.CoordinatesHud;
import com.gadzo.client.modules.hud.CpsHud;
import com.gadzo.client.modules.hud.FpsHud;
import com.gadzo.client.modules.hud.FrametimeGraphHud;
import com.gadzo.client.modules.hud.HardwareHud;
import com.gadzo.client.modules.hud.KeystrokesHud;
import com.gadzo.client.modules.hud.MemoryHud;
import com.gadzo.client.modules.hud.PingHud;
import com.gadzo.client.modules.hud.PlayerStatsHud;
import com.gadzo.client.modules.hud.PotionHud;
import com.gadzo.client.modules.hud.SessionHud;
import com.gadzo.client.modules.hud.SpeedHud;
import com.gadzo.client.modules.hud.TargetHud;
import com.gadzo.client.modules.hud.ToggleSprintHud;
import com.gadzo.client.modules.hud.WorldInfoHud;
import com.gadzo.client.modules.performance.DynamicFps;
import com.gadzo.client.modules.performance.EntityCulling;
import com.gadzo.client.modules.performance.ParticleLimiter;
import com.gadzo.client.modules.performance.RenderTuning;
import com.gadzo.client.modules.performance.ScreenEffects;
import com.gadzo.client.modules.performance.WeatherRender;
import com.gadzo.client.modules.survival.CompassHud;
import com.gadzo.client.modules.survival.DeathPoint;
import com.gadzo.client.modules.survival.DurabilityHud;
import com.gadzo.client.modules.survival.ExperienceHud;
import com.gadzo.client.modules.survival.FoodHud;
import com.gadzo.client.modules.survival.LightLevelHud;
import com.gadzo.client.modules.survival.SurvivalAlerts;
import com.gadzo.client.modules.survival.Waypoints;
import com.gadzo.client.modules.visual.Fullbright;
import com.gadzo.client.modules.visual.Zoom;
import com.gadzo.client.ui.notify.Notifications;
import com.gadzo.client.ui.screen.TitleScreenBranding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point.
 *
 * <p>Builds the module registry, restores the saved profile and wires up the tick, HUD and
 * shutdown hooks. Module construction happens here rather than through reflection so the
 * registration order — and therefore the order modules appear in the menu — is explicit.
 */
public class GadzoClient implements ClientModInitializer {

    public static final String MOD_ID = "gadzo";
    public static final String NAME = "GADZO Client";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static final ModuleManager MODULES = new ModuleManager();

    private static RenderTuning renderTuning;
    private static Waypoints waypoints;

    public static ModuleManager modules() {
        return MODULES;
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("{} v{} initialising", NAME, VERSION);

        // Kicked off first so it has the whole of startup to finish; the Auto preset falls
        // back to Medium while it is still running.
        CpuBenchmark.startAsync();

        registerModules();
        // Only safe once every constructor has finished — see Module.markPermanent().
        MODULES.activatePermanentModules();
        HudManager.register();
        Notifications.register();
        GadzoCommands.register();
        TitleScreenBranding.register();
        Waypoints.register(waypoints);

        ClientTickEvents.END_CLIENT_TICK.register(client -> MODULES.tick());

        // GameOptions are not ready during mod init, so the saved profile is applied once the
        // game has finished starting.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (renderTuning != null) {
                renderTuning.pullFromGame();
            }
            ConfigManager.load(ConfigManager.DEFAULT_PROFILE);
            // Change listeners were suppressed during the load, so push the restored values
            // through to the game once, here.
            if (renderTuning != null && renderTuning.isEnabled()) {
                renderTuning.apply();
            }
            LOGGER.info("{} ready — {} modules registered", NAME, MODULES.all().size());

            String advice = SystemProfile.heapAdvice();
            if (advice != null) {
                LOGGER.info("Heap advisory: {}", advice);
                Notifications.warning("Memory", advice);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());
    }

    private void registerModules() {
        renderTuning = new RenderTuning();
        waypoints = new Waypoints();

        MODULES.registerAll(
                // Performance
                renderTuning,
                new DynamicFps(),
                new EntityCulling(),
                new ParticleLimiter(),
                new ScreenEffects(),
                new WeatherRender(),
                new FrametimeGraphHud(),

                // HUD
                new FpsHud(),
                new CpsHud(),
                new PingHud(),
                new CoordinatesHud(),
                new MemoryHud(),
                new ClockHud(),
                new KeystrokesHud(),
                new ArmorHud(),
                new PotionHud(),
                new ToggleSprintHud(),
                new HardwareHud(),
                new SpeedHud(),
                new WorldInfoHud(),
                new PlayerStatsHud(),

                // Combat
                new ComboHud(),
                new TargetHud(),

                // Visual
                new Fullbright(),
                new Zoom(),

                // Survival
                waypoints,
                new CompassHud(),
                new DeathPoint(),
                new LightLevelHud(),
                new FoodHud(),
                new ExperienceHud(),
                new DurabilityHud(),
                new SurvivalAlerts(),
                new SurvivalHelperLauncher(),

                // Create
                new KineticHud(),
                new StressAlert(),
                new CreateHelperLauncher(),

                // Client
                new SessionHud(),
                new MenuLauncher(),
                new HudEditorLauncher(),
                new ClientSettings());
    }

    /**
     * Routes a raw key press from the keyboard mixin to module keybinds.
     *
     * <p>Kept as a static hook so the mixin does not need to reach into the manager directly.
     */
    public static void onKeyPressed(int key) {
        MODULES.handleKeyPress(key);
    }

    /** Counterpart to {@link #onKeyPressed(int)} for hold-style bindings such as zoom. */
    public static void onKeyReleased(int key) {
        MODULES.handleKeyRelease(key);
    }
}
