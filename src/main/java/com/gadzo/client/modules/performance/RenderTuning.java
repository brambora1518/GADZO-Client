package com.gadzo.client.modules.performance;

import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.EnumSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.core.system.SystemProfile;
import com.gadzo.client.util.Mc;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.ParticlesMode;

/**
 * One place to drive the vanilla render options that actually move the frame rate.
 *
 * <p>These all map onto real {@code GameOptions} entries rather than custom rendering, which is
 * what makes them safe: nothing here reimplements a render path, so there is no chance of a
 * visual desync with the rest of the game. The value is in exposing them together, applying
 * them as a coherent preset, and putting them one keystroke away instead of four menus deep.
 *
 * <p>Changes are pushed on enable and whenever a setting changes, not every tick — writing
 * options continuously would fight the vanilla options screen.
 */
public class RenderTuning extends Module {

    /** Named bundles of the settings below. */
    public enum Preset {
        CUSTOM("Custom"),
        AUTO("Auto-detect"),
        QUALITY("Quality"),
        BALANCED("Balanced"),
        PERFORMANCE("Performance"),
        EXTREME("Extreme");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final EnumSetting<Preset> preset;
    private final NumberSetting renderDistance;
    private final NumberSetting simulationDistance;
    private final NumberSetting entityDistance;
    private final NumberSetting biomeBlend;
    private final NumberSetting mipmap;
    private final EnumSetting<ParticlesMode> particles;
    private final EnumSetting<CloudRenderMode> clouds;
    private final BooleanSetting ambientOcclusion;
    private final BooleanSetting vignette;
    private final BooleanSetting entityShadows;
    private final BooleanSetting viewBobbing;

    /** Guards against the preset applier re-triggering its own change listeners. */
    private boolean applying;

    public RenderTuning() {
        super("Render tuning", "Render distance, culling budget and visual extras",
                ModuleCategory.PERFORMANCE);
        markPermanent();

        this.preset = addEnum("Preset", Preset.CUSTOM, "Apply a bundle of the settings below");
        this.preset.onChange(value -> applyPreset(value));

        this.renderDistance = addNumber("Render distance", 12, 2, 32, 1,
                "Chunks drawn around you — the biggest single frame-rate lever")
                .suffix(" chunks");
        this.simulationDistance = addNumber("Simulation distance", 10, 5, 32, 1,
                "Chunks that tick entities and blocks")
                .suffix(" chunks");
        this.entityDistance = addNumber("Entity distance", 100, 50, 500, 25,
                "Percentage of the normal entity render range")
                .suffix("%");
        this.biomeBlend = addNumber("Biome blend", 2, 0, 7, 1,
                "Smoothing between biome colours; costs chunk rebuild time");
        this.mipmap = addNumber("Mipmap levels", 4, 0, 4, 1,
                "Leave at 4 unless VRAM-starved — lowering it usually costs frames, not saves them");
        this.particles = addEnum("Particles", ParticlesMode.ALL, "Vanilla particle detail");
        this.clouds = addEnum("Clouds", CloudRenderMode.FANCY, "Cloud rendering mode");
        this.ambientOcclusion = addBool("Ambient occlusion", true, "Soft corner shading");
        this.vignette = addBool("Vignette", true, "Darkened screen edges");
        this.entityShadows = addBool("Entity shadows", true, "Blob shadows under entities");
        this.viewBobbing = addBool("View bobbing", true, "Camera sway while walking");

        // Any manual edit re-applies the whole set and drops the preset back to Custom.
        // Writes coming from a config load are excluded, or restoring a saved preset would
        // immediately relabel it as Custom.
        for (var setting : getSettings()) {
            if (setting != preset) {
                setting.onChange(ignored -> {
                    if (!applying && !ConfigManager.isLoading()) {
                        preset.setValue(Preset.CUSTOM);
                        apply();
                    }
                });
            }
        }
    }

    private void applyPreset(Preset value) {
        if (applying || value == Preset.CUSTOM) {
            return;
        }
        applying = true;
        try {
            // Mipmaps stay at 4 in every preset. Dropping them is a habit carried over from
            // cards with under a gigabyte of VRAM; on anything modern it costs performance
            // rather than saving it, because unmipmapped distant terrain thrashes the texture
            // cache. The levers that actually pay are the CPU-side ones — render and
            // simulation distance, entity range, biome blend.
            switch (value) {
                case QUALITY -> set(16, 12, 150, 5, 4, ParticlesMode.ALL, CloudRenderMode.FANCY,
                        true, true, true, true);
                case BALANCED -> set(12, 10, 100, 2, 4, ParticlesMode.DECREASED, CloudRenderMode.FAST,
                        true, true, true, true);
                case PERFORMANCE -> set(8, 6, 75, 0, 4, ParticlesMode.DECREASED, CloudRenderMode.OFF,
                        false, false, false, true);
                case EXTREME -> set(5, 5, 50, 0, 4, ParticlesMode.MINIMAL, CloudRenderMode.OFF,
                        false, false, false, false);
                case AUTO -> applyAutoPreset();
                default -> {
                    // CUSTOM is handled above.
                }
            }
        } finally {
            applying = false;
        }
        apply();
    }

    /**
     * Picks values from the detected hardware tier.
     *
     * <p>Deliberately biased towards the CPU-side settings: MinecraftClient saturates draw-call
     * submission and chunk meshing long before it saturates a discrete GPU's shader units, so
     * a mid-range discrete card paired with a modest CPU wants a lower render distance rather
     * than lower visual fidelity.
     */
    private void applyAutoPreset() {
        SystemProfile.Tier tier = SystemProfile.detectTier();
        switch (tier) {
            case ULTRA -> set(20, 14, 150, 5, 4, ParticlesMode.ALL, CloudRenderMode.FANCY,
                    true, true, true, true);
            case HIGH -> set(14, 10, 125, 3, 4, ParticlesMode.ALL, CloudRenderMode.FANCY,
                    true, true, true, true);
            case MEDIUM -> set(10, 8, 100, 1, 4, ParticlesMode.DECREASED, CloudRenderMode.FAST,
                    true, false, true, true);
            case LOW -> set(7, 6, 75, 0, 4, ParticlesMode.DECREASED, CloudRenderMode.OFF,
                    false, false, false, true);
        }
    }

    private void set(int render, int simulation, int entity, int blend, int mip,
                     ParticlesMode particleStatus, CloudRenderMode cloudStatus,
                     boolean ao, boolean vig, boolean shadows, boolean bobbing) {
        renderDistance.setValue((double) render);
        simulationDistance.setValue((double) simulation);
        entityDistance.setValue((double) entity);
        biomeBlend.setValue((double) blend);
        mipmap.setValue((double) mip);
        particles.setValue(particleStatus);
        clouds.setValue(cloudStatus);
        ambientOcclusion.setValue(ao);
        vignette.setValue(vig);
        entityShadows.setValue(shadows);
        viewBobbing.setValue(bobbing);
    }

    /** Reads the current vanilla options in, so the module starts in sync with the game. */
    public void pullFromGame() {
        GameOptions options = Mc.client() == null ? null : Mc.client().options;
        if (options == null) {
            return;
        }
        applying = true;
        try {
            renderDistance.setValue((double) options.getViewDistance().getValue());
            simulationDistance.setValue((double) options.getSimulationDistance().getValue());
            entityDistance.setValue(options.getEntityDistanceScaling().getValue() * 100.0);
            biomeBlend.setValue((double) options.getBiomeBlendRadius().getValue());
            mipmap.setValue((double) options.getMipmapLevels().getValue());
            particles.setValue(options.getParticles().getValue());
            clouds.setValue(options.getCloudRenderMode().getValue());
            ambientOcclusion.setValue(options.getAo().getValue());
            entityShadows.setValue(options.getEntityShadows().getValue());
            viewBobbing.setValue(options.getBobView().getValue());
        } finally {
            applying = false;
        }
    }

    /** Writes every value through to the game. */
    public void apply() {
        GameOptions options = Mc.client() == null ? null : Mc.client().options;
        if (options == null) {
            return;
        }
        options.getViewDistance().setValue(renderDistance.getInt());
        options.getSimulationDistance().setValue(simulationDistance.getInt());
        options.getEntityDistanceScaling().setValue(entityDistance.get() / 100.0);
        options.getBiomeBlendRadius().setValue(biomeBlend.getInt());
        options.getMipmapLevels().setValue(mipmap.getInt());
        options.getParticles().setValue(particles.get());
        options.getCloudRenderMode().setValue(clouds.get());
        options.getAo().setValue(ambientOcclusion.get());
        options.getEntityShadows().setValue(entityShadows.get());
        options.getBobView().setValue(viewBobbing.get());
    }

    @Override
    protected void onEnable() {
        apply();
    }
}
