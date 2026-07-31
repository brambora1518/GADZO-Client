package com.gadzo.client.modules.survival;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.survival.Waypoint;
import com.gadzo.client.survival.WaypointStore;
import com.gadzo.client.ui.notify.Notifications;
import com.gadzo.client.util.Mc;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Marks places in the world and draws them from a distance.
 *
 * <p>Each waypoint gets a tall wireframe beam and a floating label showing its name and how far
 * away it is. The keybind drops one where you stand; {@code /gadzo wp} manages them by name.
 *
 * <p>The beam is depth-tested and the label is not. That split is deliberate: a beam that
 * punched through terrain would be a wall-hack in everything but name, while a label floating
 * over a hill is the same information a compass gives — a direction and a distance — and is
 * what makes the feature useful for finding your way back rather than for seeing through rock.
 */
public class Waypoints extends Module {

    /** Full-brightness lightmap value, so the label is not dimmed by the block it sits over. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Text is drawn in a downscaled space; this is the usual nameplate scale. */
    private static final float LABEL_SCALE = 0.025f;

    private final BooleanSetting beams;
    private final BooleanSetting labels;
    private final BooleanSetting showDistance;
    private final NumberSetting maxDistance;
    private final NumberSetting beamHeight;
    private final NumberSetting nearFade;

    public Waypoints() {
        super("Waypoints", "Mark places and find them again", ModuleCategory.SURVIVAL);
        this.beams = addBool("Beams", true, "Draw a vertical marker at each waypoint");
        this.labels = addBool("Labels", true, "Float the name above the waypoint");
        this.showDistance = addBool("Distance", true, "Include the distance in the label");
        this.maxDistance = addNumber("Max distance", 512, 32, 4096, 32,
                "Stop drawing waypoints further away than this")
                .suffix(" m");
        this.beamHeight = addNumber("Beam height", 24, 4, 128, 4,
                "How tall the marker beam is")
                .suffix(" m");
        this.nearFade = addNumber("Hide within", 4, 0, 32, 1,
                "Hide a waypoint once you are practically standing on it")
                .suffix(" m");
        getKeybind().setValue(GLFW.GLFW_KEY_N);
    }

    /**
     * Drops a waypoint at the player's feet.
     *
     * <p>Auto-names it rather than opening a text field, because the point of a keybind is that
     * it works while something is chasing you. Renaming is a chat command away.
     */
    @Override
    public void onKeybindPressed() {
        PlayerEntity player = Mc.player();
        String dimension = WaypointStore.currentDimension();
        if (player == null || dimension == null) {
            return;
        }
        String name = nextAutomaticName();
        Waypoint waypoint = new Waypoint(name,
                (int) Math.floor(player.getX()),
                (int) Math.floor(player.getY()),
                (int) Math.floor(player.getZ()),
                dimension, WaypointStore.DEFAULT_COLOR, true);

        if (!WaypointStore.add(waypoint)) {
            return;
        }
        String where = name + " at " + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z();

        // Keybinds fire whether or not the module is on, so pressing this while it is off
        // would silently save a waypoint that never appears. Say so rather than swallow it.
        if (isEnabled()) {
            Notifications.success("Waypoint saved", where);
        } else {
            Notifications.info("Waypoint saved", where
                    + " — turn Waypoints on to see it in the world.");
        }
    }

    /** Picks the lowest unused "Point N" so repeated presses do not overwrite each other. */
    private String nextAutomaticName() {
        int index = 1;
        while (WaypointStore.byName("Point " + index) != null) {
            index++;
        }
        return "Point " + index;
    }

    // -- rendering ------------------------------------------------------------------------

    /**
     * Hooks the world render pass.
     *
     * <p>Called once at startup with the constructed module. Fabric's events cannot be
     * unregistered, so the callback checks the module's state each frame rather than
     * subscribing and unsubscribing on toggle.
     */
    public static void register(Waypoints module) {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (module.isEnabled()) {
                module.renderWorld(context);
            }
        });
    }

    private void renderWorld(WorldRenderContext context) {
        MinecraftClient client = Mc.client();
        if (client == null || client.player == null) {
            return;
        }
        List<Waypoint> waypoints = WaypointStore.inCurrentDimension();
        if (waypoints.isEmpty()) {
            return;
        }

        Camera camera = context.camera();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        Vec3d eye = camera.getPos();
        double max = maxDistance.get();
        double near = nearFade.get();

        matrices.push();
        // The world render pass leaves the stack at the camera's orientation but at the world
        // origin, so everything drawn here is offset by the camera position by hand.
        matrices.translate(-eye.x, -eye.y, -eye.z);

        for (Waypoint waypoint : waypoints) {
            if (!waypoint.visible()) {
                continue;
            }
            double distance = waypoint.distanceTo(eye);
            if (distance > max || distance < near) {
                continue;
            }
            if (beams.get()) {
                drawBeam(matrices, consumers, waypoint);
            }
            if (labels.get()) {
                drawLabel(matrices, consumers, camera, waypoint, distance, client.textRenderer);
            }
        }

        matrices.pop();

        // Text is queued on a buffer that the world pass does not flush for us.
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
    }

    private void drawBeam(MatrixStack matrices, VertexConsumerProvider consumers,
                          Waypoint waypoint) {
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        int color = waypoint.color();
        float alpha = ((color >> 24) & 0xFF) / 255.0f;
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

        double x = waypoint.x();
        double y = waypoint.y();
        double z = waypoint.z();
        double height = beamHeight.get();

        // A slim box rather than a single line: one line is nearly invisible at range, and a
        // box gives the marker a readable silhouette from any angle.
        WorldRenderer.drawBox(matrices, lines,
                x + 0.35, y, z + 0.35,
                x + 0.65, y + height, z + 0.65,
                red, green, blue, alpha);
    }

    private void drawLabel(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera,
                           Waypoint waypoint, double distance, TextRenderer font) {
        String text = showDistance.get()
                ? waypoint.name() + "  " + Math.round(distance) + "m"
                : waypoint.name();

        matrices.push();
        matrices.translate(waypoint.x() + 0.5, waypoint.y() + beamHeight.get() + 0.5,
                waypoint.z() + 0.5);
        // Face the camera, then flip: text is drawn with y increasing downwards.
        matrices.multiply(camera.getRotation());
        matrices.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float halfWidth = -font.getWidth(text) / 2.0f;

        font.draw(text, halfWidth, 0, waypoint.color(), false, matrix, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, FULL_BRIGHT);

        matrices.pop();
    }
}
