package com.gadzo.client.modules.performance;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.util.Mc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Skips rendering entities that are far away or cheap to lose.
 *
 * <p>Crowded servers spend a large share of their frame time on entities the player cannot
 * meaningfully see — dropped items behind a wall, item frames across a build, armour stands
 * in a distant plot. Culling those is the single biggest render win available without
 * touching chunk rendering.
 *
 * <p>The decision itself is made in {@link #shouldCull(Entity)}, which a mixin on the entity
 * render dispatcher consults. Players are never culled: losing a player model in PvP would
 * be a functional change, not an optimisation.
 */
public class EntityCulling extends Module {

    private static EntityCulling instance;

    private final NumberSetting maxDistance;
    private final NumberSetting itemDistance;
    private final BooleanSetting cullItems;
    private final BooleanSetting cullItemFrames;
    private final BooleanSetting cullArmorStands;
    private final BooleanSetting keepPlayers;

    public EntityCulling() {
        super("Entity culling", "Stop drawing entities you cannot usefully see",
                ModuleCategory.PERFORMANCE);
        instance = this;

        this.maxDistance = addNumber("Max distance", 64, 16, 256, 4,
                "Entities beyond this many blocks are not drawn")
                .suffix("m");
        this.cullItems = addBool("Cull dropped items", true, "Hide distant item drops");
        this.itemDistance = add(new NumberSetting("Item distance", 24, 8, 128, 4)
                .suffix("m")
                .<NumberSetting>describe("Dropped items are culled past this distance")
                .visibleWhen(() -> this.cullItems.get()));
        this.cullItemFrames = addBool("Cull item frames", true, "Hide distant item frames");
        this.cullArmorStands = addBool("Cull armour stands", false, "Hide distant armour stands");
        this.keepPlayers = addBool("Always show players", true,
                "Never cull players, whatever the distance");
    }

    public static EntityCulling get() {
        return instance;
    }

    /**
     * Whether the given entity should be skipped this frame.
     *
     * <p>Called from the render path for every entity, so it stays allocation-free and does
     * the cheap checks first.
     */
    public boolean shouldCull(Entity entity) {
        if (!isEnabled() || entity == null) {
            return false;
        }
        if (entity == Mc.player()) {
            return false;
        }
        if (keepPlayers.get() && entity instanceof Player) {
            return false;
        }

        double limit = maxDistance.get();
        if (entity instanceof ItemEntity) {
            if (!cullItems.get()) {
                return false;
            }
            limit = Math.min(limit, itemDistance.get());
        } else if (entity instanceof ItemFrame) {
            if (!cullItemFrames.get()) {
                return false;
            }
        } else if (entity instanceof ArmorStand) {
            if (!cullArmorStands.get()) {
                return false;
            }
        }

        if (Mc.player() == null) {
            return false;
        }
        // Squared comparison: avoids a sqrt per entity per frame.
        return entity.distanceToSqr(Mc.player()) > limit * limit;
    }
}
