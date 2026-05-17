package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ThreadLocalRandom;

public class BucketHandler {

    private static final int MAX_DISPLAYS = 10;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.is(Items.BUCKET)) return;

        BlockPos  pos      = event.getPos();
        Direction face     = event.getFace();
        BlockPos  adjacent = pos.relative(face);

        FluidState fluidAtPos      = serverLevel.getFluidState(pos);
        FluidState fluidAtAdjacent = serverLevel.getFluidState(adjacent);

        BlockPos   fluidPos   = null;
        FluidState fluidState = null;

        if (fluidAtPos.isSource()) {
            fluidPos   = pos;
            fluidState = fluidAtPos;
        } else if (fluidAtAdjacent.isSource()) {
            fluidPos   = adjacent;
            fluidState = fluidAtAdjacent;
        }

        if (fluidPos == null) return;

        ItemStack resultBucket;
        if (fluidState.is(Fluids.WATER)) {
            resultBucket = new ItemStack(Items.WATER_BUCKET);
        } else if (fluidState.is(Fluids.LAVA)) {
            resultBucket = new ItemStack(Items.LAVA_BUCKET);
        } else {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        serverLevel.setBlock(fluidPos,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

        held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(event.getHand(), ItemStack.EMPTY);

        spawnDisplay(serverLevel, player, resultBucket);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.is(Items.BUCKET) && !held.is(Items.WATER_BUCKET)) return;
        if (!(event.getTarget() instanceof Bucketable bucketable)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        ItemStack resultBucket = bucketable.getBucketItemStack();
        event.getTarget().discard();

        held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(event.getHand(), ItemStack.EMPTY);

        spawnDisplay(serverLevel, player, resultBucket);
    }

    private void spawnDisplay(ServerLevel level, ServerPlayer player, ItemStack item) {
        int existing = level.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        if (existing >= MAX_DISPLAYS) return;

        double yawRad = Math.toRadians(player.getYRot());
        double spawnX = player.getX() - Math.sin(yawRad) * 1.5;
        double spawnY = player.getY() + 1.2;
        double spawnZ = player.getZ() + Math.cos(yawRad) * 1.5;

        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(
                level, item, seed, spawnX, spawnY, spawnZ, player.getYRot());
        level.addFreshEntity(display);

        level.playSound(null, BlockPos.containing(spawnX, spawnY, spawnZ),
                LimboSounds.LIMBO_MUSIC.get(), SoundSource.RECORDS, 4.0f, 1.0f);
    }
}