package com.gadzo.client.survival;

import com.gadzo.client.GadzoClient;
import com.gadzo.client.core.config.ConfigManager;
import com.gadzo.client.util.Mc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persists waypoints, keyed by world.
 *
 * <p>Deliberately kept out of the profile system. A profile is a set of preferences a player
 * swaps between — a PvP layout and a building layout — while waypoints are facts about a world.
 * Loading a different profile should not lose the way home, and carrying one world's waypoints
 * into another would put beams over empty ground.
 *
 * <p>The world key is the server address on multiplayer and the save name in singleplayer.
 * Neither is a perfect identifier — a renamed save starts fresh, and two servers behind one
 * address share a list — but both are stable for the case that matters, and the alternative
 * (the world seed, or a level UUID) is not available to the client on a remote server.
 */
public final class WaypointStore {

    private static final String FILE_NAME = "waypoints.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Default beam colour, matching the client accent's usual hue rather than a raw red. */
    public static final int DEFAULT_COLOR = 0xFF4DA3FF;

    /** Colour used for automatically recorded death points. */
    public static final int DEATH_COLOR = 0xFFE05252;

    /** worldKey -> waypoints. Insertion-ordered so the file stays readable. */
    private static final Map<String, List<Waypoint>> WORLDS = new LinkedHashMap<>();

    private static boolean loaded;

    private WaypointStore() {
    }

    private static Path file() {
        return ConfigManager.configDirectory().resolve(FILE_NAME);
    }

    /**
     * Identifier for the world the player is currently in.
     *
     * <p>Returns {@code null} when there is no world, which every caller treats as "do nothing"
     * rather than as an error — the title screen is a legitimate place to be.
     */
    public static String currentWorldKey() {
        MinecraftClient client = Mc.client();
        if (client == null || client.world == null) {
            return null;
        }
        if (client.isInSingleplayer() && client.getServer() != null) {
            return "single/" + client.getServer().getSaveProperties().getLevelName();
        }
        ServerInfo server = client.getCurrentServerEntry();
        return server != null ? "server/" + server.address : "unknown";
    }

    /** Registry id of the dimension the player is in, or {@code null} outside a world. */
    public static String currentDimension() {
        MinecraftClient client = Mc.client();
        if (client == null || client.world == null) {
            return null;
        }
        return client.world.getRegistryKey().getValue().toString();
    }

    // -- access ---------------------------------------------------------------------------

    /** Every waypoint in the current world, across all dimensions. */
    public static List<Waypoint> allInWorld() {
        ensureLoaded();
        String key = currentWorldKey();
        if (key == null) {
            return List.of();
        }
        return List.copyOf(WORLDS.getOrDefault(key, List.of()));
    }

    /** Waypoints in the dimension the player is standing in — the ones worth drawing. */
    public static List<Waypoint> inCurrentDimension() {
        String dimension = currentDimension();
        if (dimension == null) {
            return List.of();
        }
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : allInWorld()) {
            if (waypoint.dimension().equals(dimension)) {
                result.add(waypoint);
            }
        }
        return result;
    }

    /** The closest waypoints in this dimension, nearest first. */
    public static List<Waypoint> nearest(Vec3d from, int limit) {
        List<Waypoint> sorted = new ArrayList<>(inCurrentDimension());
        sorted.sort(Comparator.comparingDouble(waypoint -> waypoint.squaredDistanceTo(from)));
        return sorted.size() <= limit ? sorted : sorted.subList(0, limit);
    }

    public static Waypoint byName(String name) {
        String needle = normalise(name);
        for (Waypoint waypoint : allInWorld()) {
            if (normalise(waypoint.name()).equals(needle)) {
                return waypoint;
            }
        }
        return null;
    }

    private static String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    // -- mutation -------------------------------------------------------------------------

    /**
     * Adds a waypoint, replacing any existing one with the same name in this world.
     *
     * @return false when there is no world to attach it to
     */
    public static boolean add(Waypoint waypoint) {
        ensureLoaded();
        String key = currentWorldKey();
        if (key == null) {
            return false;
        }
        List<Waypoint> list = WORLDS.computeIfAbsent(key, unused -> new ArrayList<>());
        list.removeIf(existing -> normalise(existing.name()).equals(normalise(waypoint.name())));
        list.add(waypoint);
        save();
        return true;
    }

    public static boolean remove(String name) {
        ensureLoaded();
        String key = currentWorldKey();
        if (key == null) {
            return false;
        }
        List<Waypoint> list = WORLDS.get(key);
        if (list == null) {
            return false;
        }
        boolean removed = list.removeIf(existing -> normalise(existing.name()).equals(normalise(name)));
        if (removed) {
            save();
        }
        return removed;
    }

    /** Replaces a waypoint in place, matching on name. */
    public static boolean replace(Waypoint waypoint) {
        ensureLoaded();
        String key = currentWorldKey();
        if (key == null) {
            return false;
        }
        List<Waypoint> list = WORLDS.get(key);
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (normalise(list.get(i).name()).equals(normalise(waypoint.name()))) {
                list.set(i, waypoint);
                save();
                return true;
            }
        }
        return false;
    }

    public static int clearCurrentWorld() {
        ensureLoaded();
        String key = currentWorldKey();
        if (key == null) {
            return 0;
        }
        List<Waypoint> list = WORLDS.remove(key);
        save();
        return list == null ? 0 : list.size();
    }

    // -- persistence ----------------------------------------------------------------------

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject worlds = root.getAsJsonObject().getAsJsonObject("worlds");
            if (worlds == null) {
                return;
            }
            for (String key : worlds.keySet()) {
                JsonArray array = worlds.getAsJsonArray(key);
                List<Waypoint> list = new ArrayList<>();
                for (JsonElement element : array) {
                    Waypoint waypoint = readWaypoint(element);
                    if (waypoint != null) {
                        list.add(waypoint);
                    }
                }
                WORLDS.put(key, list);
            }
        } catch (IOException | RuntimeException e) {
            // A corrupt waypoint file must not stop the client loading. The player loses the
            // list, which is bad; a crash loop at startup would be worse.
            GadzoClient.LOGGER.warn("Could not read waypoints; starting with an empty list", e);
        }
    }

    /** Tolerant of missing fields so a file written by an older build still loads. */
    private static Waypoint readWaypoint(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("name") || !object.has("x")) {
            return null;
        }
        return new Waypoint(
                object.get("name").getAsString(),
                object.get("x").getAsInt(),
                object.has("y") ? object.get("y").getAsInt() : 64,
                object.has("z") ? object.get("z").getAsInt() : 0,
                object.has("dimension") ? object.get("dimension").getAsString()
                        : "minecraft:overworld",
                object.has("color") ? object.get("color").getAsInt() : DEFAULT_COLOR,
                !object.has("visible") || object.get("visible").getAsBoolean());
    }

    /** Written atomically, like the profile files, so an interrupted save is not destructive. */
    public static synchronized void save() {
        JsonObject worlds = new JsonObject();
        for (Map.Entry<String, List<Waypoint>> entry : WORLDS.entrySet()) {
            JsonArray array = new JsonArray();
            for (Waypoint waypoint : entry.getValue()) {
                JsonObject object = new JsonObject();
                object.addProperty("name", waypoint.name());
                object.addProperty("x", waypoint.x());
                object.addProperty("y", waypoint.y());
                object.addProperty("z", waypoint.z());
                object.addProperty("dimension", waypoint.dimension());
                object.addProperty("color", waypoint.color());
                object.addProperty("visible", waypoint.visible());
                array.add(object);
            }
            worlds.add(entry.getKey(), array);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", GadzoClient.VERSION);
        root.add("worlds", worlds);

        Path target = file();
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            GadzoClient.LOGGER.error("Failed to save waypoints", e);
        }
    }
}
