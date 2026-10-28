package com.villageroads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/** +10% movement speed (configurable) while standing on road blocks (tag villageroads:fast_roads incl. Realistic Terrain roads). */
public class RoadSpeed {
    private static final UUID ID = UUID.fromString("2c9d3f6a-8b0e-4d55-a1c2-4f7e1a9b7e01");
    private static final TagKey<Block> FAST = TagKey.create(Registries.BLOCK, new ResourceLocation(VillageRoads.MODID, "fast_roads"));

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 5 != 0) {
            return;
        }
        if (!RoadConfig.SPEED_BONUS_ENABLED.get()) {
            remove(player);
            return;
        }
        Entity vehicle = player.getVehicle();
        LivingEntity body = player;
        if (vehicle instanceof LivingEntity mount && mount.getControllingPassenger() == player) {
            body = mount;
            remove(player);
        }
        if (!body.onGround()) {
            return; // keep the current state while jumping
        }
        BlockPos ground = BlockPos.containing(body.getX(), body.getY() - 0.5000001D, body.getZ());
        BlockState state = body.level().getBlockState(ground);
        BlockState feet = body.level().getBlockState(body.blockPosition());
        if (state.is(FAST) || feet.is(FAST)) {
            apply(body);
        } else {
            remove(body);
        }
    }

    @SubscribeEvent
    public void onDismount(EntityMountEvent event) {
        if (event.isDismounting() && !event.getEntityMounting().level().isClientSide()
                && event.getEntityBeingMounted() instanceof LivingEntity mount) {
            remove(mount);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        remove(p);
        if (p.getVehicle() instanceof LivingEntity mount) {
            remove(mount);
        }
    }

    private static void apply(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getModifier(ID) == null) {
            attr.addTransientModifier(new AttributeModifier(ID, "village_roads_speed",
                    RoadConfig.SPEED_BONUS.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void remove(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getModifier(ID) != null) {
            attr.removeModifier(ID);
        }
    }
}
