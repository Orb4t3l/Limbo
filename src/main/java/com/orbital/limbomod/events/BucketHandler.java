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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ThreadLocalRandom;

public class BucketHandler {

    private static final int MAX_DISPLAYS = 10;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isCreative()) return;

        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!held.is(Items.BUCKET)) return;

        Level     level    = event.getLevel();
        BlockPos  pos      = event.getPos();
        Direction face     = event.getFace();
        BlockPos  adjacent = pos.relative(face);

        FluidState fluidAtPos      = level.getFluidState(pos);
        FluidState fluidAtAdjacent = level.getFluidState(adjacent);

        BlockPos   fluidPos   = null;
        FluidState fluidState = null;

        if (fluidAtPos.isSource() && isHandledFluid(fluidAtPos)) {
            fluidPos   = pos;
            fluidState = fluidAtPos;
        } else if (fluidAtAdjacent.isSource() && isHandledFluid(fluidAtAdjacent)) {
            fluidPos   = adjacent;
            fluidState = fluidAtAdjacent;
        }

        if (fluidPos == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (level.isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        handleFluidPickup(serverLevel, player, fluidPos, fluidState, event.getHand());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().isCreative()) return;

        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!held.is(Items.BUCKET)) return;

        Level level = event.getLevel();

        HitResult hit = event.getEntity().pick(5.0, 0.0f, true);
        if (!(hit instanceof BlockHitResult blockHit)) return;

        BlockPos   pos       = blockHit.getBlockPos();
        FluidState fluidState = level.getFluidState(pos);

        if (!fluidState.isSource() || !isHandledFluid(fluidState)) return;

        event.setCanceled(true);

        if (level.isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        handleFluidPickup(serverLevel, player, pos, fluidState,
                event.getHand());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().isCreative()) return;

        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!held.is(Items.BUCKET) && !held.is(Items.WATER_BUCKET)) return;
        if (!(event.getTarget() instanceof Bucketable)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (event.getLevel().isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getTarget() instanceof Bucketable bucketable)) return;

        ItemStack resultBucket = bucketable.getBucketItemStack();
        event.getTarget().discard();

        held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(event.getHand(), ItemStack.EMPTY);
        player.inventoryMenu.broadcastFullState();

        spawnDisplay(serverLevel, player, resultBucket);
    }

    private void handleFluidPickup(ServerLevel serverLevel, ServerPlayer player,
                                   BlockPos fluidPos, FluidState fluidState,
                                   net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        ItemStack resultBucket = fluidState.is(Fluids.WATER)
                ? new ItemStack(Items.WATER_BUCKET)
                : new ItemStack(Items.LAVA_BUCKET);

        serverLevel.setBlock(fluidPos, Blocks.AIR.defaultBlockState(), 3);

        held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
        player.inventoryMenu.broadcastFullState();

        spawnDisplay(serverLevel, player, resultBucket);
    }

    private static boolean isHandledFluid(FluidState state) {
        return state.is(Fluids.WATER) || state.is(Fluids.LAVA);
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