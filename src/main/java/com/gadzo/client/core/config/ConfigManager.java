package com.gadzo.client.core.config;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.hud.HudAnchor;
import com.gadzo.client.core.hud.HudModule;
import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.setting.Setting;
import com.gadzo.client.ui.Theme;
import com.gadzo.client.util.ColorUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes the client's configuration.
 *
 * <p>Everything lives under {@code .minecraft/config/gadzo/}, one JSON file per profile, so a
 * player can keep separate layouts for PvP and building and swap between them. Writes go to a
 * temporary file first and are then moved into place — a crash mid-save leaves the previous
 * config intact rather than a truncated one.
 */
public final class ConfigManager {

    private static final String DIRECTORY = "gadzo";
    private static final String EXTENSION = ".json";
    public static final String DEFAULT_PROFILE = "default";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String activeProfile = DEFAULT_PROFILE;

    /**
     * Set while a profile is being applied.
     *
     * <p>Settings notify their listeners on change, and some modules react by recomputing
     * derived state — {@code RenderTuning} drops its preset to Custom when any individual
     * value is edited. During a load those writes are the config being restored, not the
     * user editing, so listeners consult this flag to tell the two apart.
     */
    private static volatile boolean loading;

    private ConfigManager() {
    }

    /** Whether a profile is currently being applied to the live module set. */
    public static boolean isLoading() {
        return loading;
    }

    public static Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY);
    }

    public static Path profilePath(String profile) {
        return configDirectory().resolve(sanitiseProfileName(profile) + EXTENSION);
    }

    public static String activeProfile() {
        return activeProfile;
    }

    /** Strips anything that would let a profile name escape the config directory. */
    private static String sanitiseProfileName(String profile) {
        String cleaned = profile.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return cleaned.isEmpty() ? DEFAULT_PROFILE : cleaned;
    }

    public static List<String> listProfiles() {
        List<String> profiles = new ArrayList<>();
        Path directory = configDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of(DEFAULT_PROFILE);
        }
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                    .map(path -> {
                        String name = path.getFileName().toString();
                        return name.substring(0, name.length() - EXTENSION.length());
                    })
                    .sorted()
                    .forEach(profiles::add);
        } catch (IOException e) {
            GadzoClient.LOGGER.warn("Could not list config profiles", e);
        }
        if (profiles.isEmpty()) {
            profiles.add(DEFAULT_PROFILE);
        }
        return profiles;
    }

    // -- saving ------------------------------------------------------------------------

    public static void save() {
        save(activeProfile);
    }

    public static void save(String profile) {
        JsonObject root = new JsonObject();
        root.addProperty("version", GadzoClient.VERSION);
        root.add("theme", writeTheme());
        root.add("modules", writeModules());

        Path target = profilePath(profile);
        try {
            Files.createDirectories(target.getParent());
            // Write beside the target, then atomically replace it, so an interrupted save
            // cannot leave a half-written config behind.
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            GadzoClient.LOGGER.info("Saved profile '{}'", profile);
        } catch (IOException e) {
            GadzoClient.LOGGER.error("Failed to save profile '{}'", profile, e);
        }
    }

    private static JsonObject writeTheme() {
        JsonObject theme = new JsonObject();
        theme.addProperty("styleVersion", Theme.STYLE_VERSION);
        theme.addProperty("appearance", Theme.appearance().name());
        theme.addProperty("accentMode", Theme.accentMode().name());
        theme.addProperty("accentPrimary", ColorUtil.toHex(Theme.accentStart()));
        theme.addProperty("accentSecondary", ColorUtil.toHex(Theme.accentEnd()));
        theme.addProperty("radius", Theme.radius());
        theme.addProperty("blur", Theme.blurEnabled());
        return theme;
    }

    private static JsonObject writeModules() {
        JsonObject modules = new JsonObject();
        for (Module module : GadzoClient.modules().all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());

            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                settings.add(setting.getId(), setting.write());
            }
            entry.add("settings", settings);

            // HUD placement is not modelled as a Setting because it has no widget of its
            // own — it is edited by dragging — so it is persisted alongside them.
            if (module instanceof HudModule hud) {
                JsonObject placement = new JsonObject();
                placement.addProperty("anchor", hud.getAnchor().name());
                placement.addProperty("offsetX", hud.getOffsetX());
                placement.addProperty("offsetY", hud.getOffsetY());
                entry.add("placement", placement);
            }

            modules.add(module.getId(), entry);
        }
        return modules;
    }

    // -- loading -----------------------------------------------------------------------

    /**
     * Loads a profile into the live module set.
     *
     * <p>Missing keys are left at their defaults, so a config written by an older build still
     * loads cleanly after new modules or settings are added.
     */
    public static void load(String profile) {
        Path source = profilePath(profile);
        if (!Files.isRegularFile(source)) {
            GadzoClient.LOGGER.info("No config for profile '{}', starting from defaults", profile);
            activeProfile = sanitiseProfileName(profile);
            return;
        }

        loading = true;
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                GadzoClient.LOGGER.warn("Profile '{}' is not a JSON object, ignoring", profile);
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("theme")) {
                readTheme(root.getAsJsonObject("theme"));
            }
            if (root.has("modules")) {
                readModules(root.getAsJsonObject("modules"));
            }
            activeProfile = sanitiseProfileName(profile);
            GadzoClient.LOGGER.info("Loaded profile '{}'", activeProfile);
        } catch (Exception e) {
            // A corrupt config must not stop the client from starting.
            GadzoClient.LOGGER.error("Failed to load profile '{}', keeping defaults", profile, e);
        } finally {
            loading = false;
        }
    }

    public static void load() {
        load(activeProfile);
    }

    /**
     * Restores the saved theme.
     *
     * <p>Values the player actively chooses — appearance, accent colours, whether blur is on —
     * are always restored. Values that are purely the client's own styling are only restored
     * when the config was written by the current {@link Theme#STYLE_VERSION}.
     *
     * <p>That distinction exists because of a real bug: corner radius is persisted, so the
     * first run wrote the then-default 8 into every profile, and every later change to that
     * default was silently overwritten on load. A restyle could not reach anyone who had
     * already launched the client once. Bumping the style version now lets the new defaults
     * through exactly once, without touching the colours someone picked on purpose.
     */
    private static void readTheme(JsonObject theme) {
        int styleVersion = theme.has("styleVersion") ? theme.get("styleVersion").getAsInt() : 0;
        boolean currentStyle = styleVersion >= Theme.STYLE_VERSION;

        if (theme.has("appearance")) {
            parseEnum(Theme.Appearance.class, theme.get("appearance").getAsString())
                    .ifPresent(Theme::setAppearance);
        }
        if (theme.has("accentMode")) {
            parseEnum(Theme.AccentMode.class, theme.get("accentMode").getAsString())
                    .ifPresent(Theme::setAccentMode);
        }
        if (theme.has("accentPrimary")) {
            Theme.setAccentPrimary(ColorUtil.parseHex(theme.get("accentPrimary").getAsString(), 0xFF5B8CFF));
        }
        if (theme.has("accentSecondary")) {
            Theme.setAccentSecondary(ColorUtil.parseHex(theme.get("accentSecondary").getAsString(), 0xFF9B5BFF));
        }
        if (theme.has("radius") && currentStyle) {
            Theme.setCornerRadius(theme.get("radius").getAsDouble());
        }
        if (theme.has("blur")) {
            Theme.setBlurEnabled(theme.get("blur").getAsBoolean());
        }
    }

    private static <E extends Enum<E>> java.util.Optional<E> parseEnum(Class<E> type, String name) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return java.util.Optional.of(constant);
            }
        }
        return java.util.Optional.empty();
    }

    private static void readModules(JsonObject modules) {
        for (Module module : GadzoClient.modules().all()) {
            if (!modules.has(module.getId())) {
                continue;
            }
            JsonObject entry = modules.getAsJsonObject(module.getId());

            if (entry.has("settings")) {
                JsonObject settings = entry.getAsJsonObject("settings");
                for (Setting<?> setting : module.getSettings()) {
                    if (settings.has(setting.getId())) {
                        setting.read(settings.get(setting.getId()));
                    }
                }
            }

            if (module instanceof HudModule hud && entry.has("placement")) {
                JsonObject placement = entry.getAsJsonObject("placement");
                if (placement.has("anchor")) {
                    parseEnum(HudAnchor.class, placement.get("anchor").getAsString())
                            .ifPresent(hud::setAnchor);
                }
                hud.setOffset(
                        placement.has("offsetX") ? placement.get("offsetX").getAsDouble() : hud.getOffsetX(),
                        placement.has("offsetY") ? placement.get("offsetY").getAsDouble() : hud.getOffsetY());
            }

            // Enabling last means onEnable() runs with the module's settings already applied.
            if (entry.has("enabled")) {
                module.restoreEnabled(entry.get("enabled").getAsBoolean());
            }
        }
    }

    public static void deleteProfile(String profile) {
        String name = sanitiseProfileName(profile);
        if (DEFAULT_PROFILE.equals(name)) {
            GadzoClient.LOGGER.warn("Refusing to delete the default profile");
            return;
        }
        try {
            Files.deleteIfExists(profilePath(name));
        } catch (IOException e) {
            GadzoClient.LOGGER.error("Failed to delete profile '{}'", name, e);
        }
    }
}
