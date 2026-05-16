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
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos   pos   = event.getPos();
        BlockState state = serverLevel.getBlockState(pos);

        if (!(state.getBlock() instanceof ChestBlock)) return;
        if (!(serverLevel.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)) return;

        CompoundTag nbt = container.serializeNBT();
        if (!nbt.contains("LootTable")) return;

        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);

        CompoundTag savedNbt = nbt.copy();

        container.setLootTable(null, 0L);
        container.clearContent();

        serverLevel.removeBlock(pos, false);

        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        if (existing >= MAX_DISPLAYS) {
            serverLevel.setBlock(pos, state, 3);
            return;
        }

        BlockPos immutablePos = pos.immutable();
        long     seed         = ThreadLocalRandom.current().nextLong();

        LimboDisplayEntity display = new LimboDisplayEntity(
                serverLevel, new ItemStack(Items.CHEST), seed,
                immutablePos.getX() + 0.5,
                immutablePos.getY() + 1.5,
                immutablePos.getZ() + 0.5,
                player.getYRot());

        display.setSuccessPlacement(immutablePos, state, savedNbt);
        serverLevel.addFreshEntity(display);

        serverLevel.playSound(null, immutablePos, LimboSounds.LIMBO_MUSIC.get(),
                SoundSource.RECORDS, 4.0f, 1.0f);
    }
}