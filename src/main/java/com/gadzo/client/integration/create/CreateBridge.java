package com.gadzo.client.integration.create;

import com.gadzo.client.GadzoClient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads live kinetic data out of the Create mod.
 *
 * <p>Everything here goes through reflection rather than a compile-time dependency, which is a
 * deliberate trade. GADZO is a general-purpose client that most people will run without Create
 * installed; a hard dependency would mean either shipping two builds or refusing to load
 * without a 30 MB mod present. Reflection costs a little type safety and buys a client that
 * simply lights up when Create appears and stays silent when it does not.
 *
 * <p>The reflection is safer than it looks. Create's own class, field and method names are not
 * remapped by the loader — only the Minecraft types inside their signatures are — so
 * {@code KineticBlockEntity.getSpeed()} is called by that exact name in both a development and
 * a production environment. The names used here have been stable across Create 0.3 to 6.x.
 *
 * <p>Every lookup is resolved once and cached. If any of them fails the whole bridge latches
 * off and logs a single line: a missing method means this build of Create moved something, and
 * retrying it every frame would turn one incompatibility into a log flood.
 */
public final class CreateBridge {

    private static final String KINETIC_BLOCK_ENTITY =
            "com.simibubi.create.content.kinetics.base.KineticBlockEntity";
    private static final String BLOCK_STRESS_VALUES =
            "com.simibubi.create.api.stress.BlockStressValues";

    private enum State {
        UNRESOLVED,
        READY,
        UNAVAILABLE
    }

    private static State state = State.UNRESOLVED;

    private static Class<?> kineticType;
    private static Method getSpeed;
    private static Method getTheoreticalSpeed;
    private static Method isOverStressed;
    private static Method isSpeedRequirementFulfilled;
    private static Method hasNetwork;
    private static Field stressField;
    private static Field capacityField;
    private static Field networkSizeField;

    private static Method getImpact;
    private static Method getCapacity;

    private CreateBridge() {
    }

    /** Whether Create is installed at all. Cheap; safe to call every frame. */
    public static boolean isCreateLoaded() {
        return FabricLoader.getInstance().isModLoaded("create");
    }

    /**
     * Whether live readings are possible.
     *
     * <p>False either because Create is absent or because its internals moved and the bridge
     * latched off.
     */
    public static boolean isLive() {
        return resolve() == State.READY;
    }

    private static synchronized State resolve() {
        if (state != State.UNRESOLVED) {
            return state;
        }
        if (!isCreateLoaded()) {
            state = State.UNAVAILABLE;
            return state;
        }
        try {
            ClassLoader loader = CreateBridge.class.getClassLoader();
            kineticType = Class.forName(KINETIC_BLOCK_ENTITY, false, loader);

            getSpeed = kineticType.getMethod("getSpeed");
            getTheoreticalSpeed = kineticType.getMethod("getTheoreticalSpeed");
            isOverStressed = kineticType.getMethod("isOverStressed");
            isSpeedRequirementFulfilled = kineticType.getMethod("isSpeedRequirementFulfilled");
            hasNetwork = kineticType.getMethod("hasNetwork");

            // Protected fields, set client-side by the network sync packet. There is no public
            // accessor for them; the goggle tooltip reads them directly for the same reason.
            stressField = accessibleField(kineticType, "stress");
            capacityField = accessibleField(kineticType, "capacity");
            networkSizeField = accessibleField(kineticType, "networkSize");

            Class<?> stressValues = Class.forName(BLOCK_STRESS_VALUES, false, loader);
            getImpact = stressValues.getMethod("getImpact", Block.class);
            getCapacity = stressValues.getMethod("getCapacity", Block.class);

            state = State.READY;
            GadzoClient.LOGGER.info("Create detected — live kinetic readings enabled");
        } catch (ReflectiveOperationException | RuntimeException e) {
            state = State.UNAVAILABLE;
            GadzoClient.LOGGER.warn("Create is installed but its kinetic API did not match what "
                    + "GADZO expects; live readings are off. {}", e.toString());
        }
        return state;
    }

    private static Field accessibleField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** Whether this block entity is part of a kinetic network. */
    public static boolean isKinetic(BlockEntity blockEntity) {
        return blockEntity != null && resolve() == State.READY
                && kineticType.isInstance(blockEntity);
    }

    /**
     * Reads the kinetic state of a block entity.
     *
     * @return the reading, or {@code null} if this is not a kinetic block or Create is absent
     */
    public static KineticReading read(BlockEntity blockEntity, String blockName) {
        if (!isKinetic(blockEntity)) {
            return null;
        }
        try {
            float speed = (Float) getSpeed.invoke(blockEntity);
            float theoretical = (Float) getTheoreticalSpeed.invoke(blockEntity);
            float stress = stressField.getFloat(blockEntity);
            float capacity = capacityField.getFloat(blockEntity);
            int networkSize = networkSizeField.getInt(blockEntity);
            boolean overStressed = (Boolean) isOverStressed.invoke(blockEntity);
            boolean speedOk = (Boolean) isSpeedRequirementFulfilled.invoke(blockEntity);
            boolean connected = (Boolean) hasNetwork.invoke(blockEntity);

            double impact = 0;
            double added = 0;
            Block block = blockEntity.getCachedState().getBlock();
            try {
                impact = (Double) getImpact.invoke(null, block);
                added = (Double) getCapacity.invoke(null, block);
            } catch (ReflectiveOperationException ignored) {
                // Per-block figures are a nicety; the network numbers above are the point.
            }

            return new KineticReading(blockName, speed, theoretical, stress, capacity,
                    networkSize, overStressed, speedOk, connected, impact, added);
        } catch (ReflectiveOperationException | RuntimeException e) {
            state = State.UNAVAILABLE;
            GadzoClient.LOGGER.warn("Reading a Create kinetic block failed; live readings are "
                    + "off for this session. {}", e.toString());
            return null;
        }
    }

    /**
     * Per-RPM stress impact of a block, straight from Create's own registry.
     *
     * <p>This is the player's actual configured value, including any datapack or config
     * override, rather than a table baked into this client at build time.
     *
     * @return the impact, or {@code -1} when it cannot be read
     */
    public static double impactOf(Block block) {
        if (resolve() != State.READY || block == null) {
            return -1;
        }
        try {
            return (Double) getImpact.invoke(null, block);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    /** Per-RPM capacity a block adds, or {@code -1} when it cannot be read. */
    public static double capacityOf(Block block) {
        if (resolve() != State.READY || block == null) {
            return -1;
        }
        try {
            return (Double) getCapacity.invoke(null, block);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
