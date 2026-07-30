package com.gadzo.client.core.command;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.system.CpuBenchmark;
import com.gadzo.client.core.system.SystemProfile;
import com.gadzo.client.ui.notify.Notifications;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Client-side {@code /gadzo} commands.
 *
 * <p>Registered through Fabric's client command API, so nothing is ever sent to the server —
 * these run entirely locally and work on any server, including vanilla ones.
 *
 * <p>Module names are matched leniently (case-insensitive, spaces or underscores both work)
 * because typing an exact display name into chat is tedious and the failure mode of a strict
 * match is a command that appears broken.
 */
public final class GadzoCommands {

    private GadzoCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommandManager.literal("gadzo")
                        .executes(ctx -> showHelp(ctx.getSource()))

                        .then(ClientCommandManager.literal("help")
                                .executes(ctx -> showHelp(ctx.getSource())))

                        .then(ClientCommandManager.literal("list")
                                .executes(ctx -> listModules(ctx.getSource(), null))
                                .then(ClientCommandManager.argument("category", StringArgumentType.word())
                                        .executes(ctx -> listModules(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "category")))))

                        .then(ClientCommandManager.literal("toggle")
                                .then(ClientCommandManager.argument("module", StringArgumentType.greedyString())
                                        .executes(ctx -> toggleModule(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "module")))))

                        .then(ClientCommandManager.literal("profile")
                                .executes(ctx -> showProfiles(ctx.getSource()))
                                .then(ClientCommandManager.literal("save")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(ctx -> saveProfile(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(ClientCommandManager.literal("load")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(ctx -> loadProfile(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"))))))

                        .then(ClientCommandManager.literal("hardware")
                                .executes(ctx -> showHardware(ctx.getSource())))

                        .then(ClientCommandManager.literal("save")
                                .executes(ctx -> {
                                    ConfigManager.save();
                                    feedback(ctx.getSource(), "Saved profile '"
                                            + ConfigManager.activeProfile() + "'.");
                                    return 1;
                                }))));
    }

    // -- helpers -------------------------------------------------------------------------

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal("[GADZO] ").formatted(Formatting.AQUA)
                .append(Text.literal(message).formatted(Formatting.WHITE)));
    }

    private static void error(FabricClientCommandSource source, String message) {
        source.sendError(Text.literal("[GADZO] " + message));
    }

    /** Normalises a name so "Entity culling", "entity_culling" and "ENTITYCULLING" all match. */
    private static String normalise(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Finds a module by loose name match.
     *
     * <p>Prefers an exact normalised match, then falls back to a unique prefix, then to a
     * unique substring — so "ent" finds "Entity culling" but an ambiguous fragment does not
     * silently pick the wrong one.
     */
    private static Module findModule(String query) {
        String needle = normalise(query);
        List<Module> all = GadzoClient.modules().all();

        for (Module module : all) {
            if (normalise(module.getName()).equals(needle)) {
                return module;
            }
        }
        Module prefixMatch = uniqueMatch(all, needle, true);
        return prefixMatch != null ? prefixMatch : uniqueMatch(all, needle, false);
    }

    private static Module uniqueMatch(List<Module> all, String needle, boolean prefix) {
        Module found = null;
        for (Module module : all) {
            String name = normalise(module.getName());
            boolean hit = prefix ? name.startsWith(needle) : name.contains(needle);
            if (hit) {
                if (found != null) {
                    // Ambiguous — refuse rather than guess.
                    return null;
                }
                found = module;
            }
        }
        return found;
    }

    // -- command bodies ------------------------------------------------------------------

    private static int showHelp(FabricClientCommandSource source) {
        feedback(source, "Commands:");
        feedback(source, "  /gadzo list [category]   — list modules");
        feedback(source, "  /gadzo toggle <module>   — turn a module on or off");
        feedback(source, "  /gadzo profile           — show profiles");
        feedback(source, "  /gadzo profile save|load <name>");
        feedback(source, "  /gadzo hardware          — detected tier and bottleneck");
        feedback(source, "  /gadzo save              — write the active profile");
        return 1;
    }

    private static int listModules(FabricClientCommandSource source, String categoryName) {
        ModuleCategory filter = null;
        if (categoryName != null) {
            for (ModuleCategory category : ModuleCategory.values()) {
                if (normalise(category.displayName()).equals(normalise(categoryName))) {
                    filter = category;
                    break;
                }
            }
            if (filter == null) {
                error(source, "Unknown category '" + categoryName + "'.");
                return 0;
            }
        }

        int shown = 0;
        for (Module module : GadzoClient.modules().all()) {
            if (module.isHidden() || (filter != null && module.getCategory() != filter)) {
                continue;
            }
            source.sendFeedback(Text.literal("  " + (module.isEnabled() ? "[on]  " : "[off] "))
                    .formatted(module.isEnabled() ? Formatting.GREEN : Formatting.DARK_GRAY)
                    .append(Text.literal(module.getName()).formatted(Formatting.WHITE))
                    .append(Text.literal("  " + module.getCategory().displayName())
                            .formatted(Formatting.DARK_GRAY)));
            shown++;
        }
        feedback(source, shown + " module(s).");
        return 1;
    }

    private static int toggleModule(FabricClientCommandSource source, String query) {
        Module module = findModule(query);
        if (module == null) {
            error(source, "No single module matches '" + query + "'.");
            return 0;
        }
        if (module.isPermanent()) {
            error(source, module.getName() + " is always on and cannot be toggled.");
            return 0;
        }
        module.toggle();
        feedback(source, module.getName() + " is now " + (module.isEnabled() ? "on" : "off") + ".");
        return 1;
    }

    private static int showProfiles(FabricClientCommandSource source) {
        feedback(source, "Active profile: " + ConfigManager.activeProfile());
        for (String profile : ConfigManager.listProfiles()) {
            source.sendFeedback(Text.literal("  " + profile)
                    .formatted(profile.equals(ConfigManager.activeProfile())
                            ? Formatting.AQUA : Formatting.GRAY));
        }
        return 1;
    }

    private static int saveProfile(FabricClientCommandSource source, String name) {
        ConfigManager.save(name);
        feedback(source, "Saved profile '" + name + "'.");
        Notifications.success("Profile saved", name);
        return 1;
    }

    private static int loadProfile(FabricClientCommandSource source, String name) {
        ConfigManager.load(name);
        feedback(source, "Loaded profile '" + ConfigManager.activeProfile() + "'.");
        Notifications.info("Profile loaded", ConfigManager.activeProfile());
        return 1;
    }

    private static int showHardware(FabricClientCommandSource source) {
        feedback(source, "GPU: " + SystemProfile.gpuName());
        feedback(source, "CPU: " + SystemProfile.cpuThreads() + " threads, score "
                + (CpuBenchmark.isReady() ? CpuBenchmark.score() + " / " + CpuBenchmark.REFERENCE_SCORE
                        : "measuring..."));
        feedback(source, "Heap: " + SystemProfile.maxHeapMb() + " MB"
                + (SystemProfile.physicalRamMb() > 0
                        ? " of " + SystemProfile.physicalRamMb() + " MB system RAM" : ""));
        feedback(source, "Tier: " + SystemProfile.detectTier());
        feedback(source, "GPU timing is not available on Minecraft 1.20.1.");

        String advice = SystemProfile.heapAdvice();
        if (advice != null) {
            source.sendFeedback(Text.literal("[GADZO] " + advice).formatted(Formatting.YELLOW));
        }
        return 1;
    }
}
