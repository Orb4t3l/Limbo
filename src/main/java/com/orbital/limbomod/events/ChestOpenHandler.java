package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ThreadLocalRandom;

public class ChestOpenHandler {

    private static final int MAX_DISPLAYS = 10;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        BlockPos   pos   = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);

        System.out.println("[ChestMod] RightClick on: " + state.getBlock().getDescriptionId());

        if (!(state.getBlock() instanceof ChestBlock)) {
            System.out.println("[ChestMod] Not a chest, skipping");
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockEntity be = serverLevel.getBlockEntity(pos);
        System.out.println("[ChestMod] BlockEntity: " + (be == null ? "NULL" : be.getClass().getSimpleName()));

        if (!(be instanceof RandomizableContainerBlockEntity container)) {
            System.out.println("[ChestMod] Not a RandomizableContainerBlockEntity");
            return;
        }

        CompoundTag nbt = container.serializeNBT();
        System.out.println("[ChestMod] NBT keys: " + nbt.getAllKeys());
        System.out.println("[ChestMod] Has LootTable: " + nbt.contains("LootTable"));

        if (!nbt.contains("LootTable")) return;

        System.out.println("[ChestMod] Intercepting chest at " + pos);

        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);

        if (player.isCreative()) return;

        CompoundTag savedNbt = nbt.copy();

        CompoundTag emptyNbt = nbt.copy();
        emptyNbt.remove("LootTable");
        emptyNbt.remove("LootTableSeed");
        emptyNbt.remove("Items");
        container.deserializeNBT(emptyNbt);

        serverLevel.removeBlock(pos, false);

        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        if (existing >= MAX_DISPLAYS) {
            serverLevel.setBlock(pos, state, 3);
            return;
        }

        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(
                serverLevel, new ItemStack(Items.CHEST), seed,
                pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                player.getYRot());

        display.setSuccessPlacement(pos, state, savedNbt);
        serverLevel.addFreshEntity(display);

        serverLevel.playSound(null, pos, LimboSounds.LIMBO_MUSIC.get(),
                SoundSource.RECORDS, 4.0f, 1.0f);

        System.out.println("[ChestMod] Display spawned successfully");
    }
}