package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboDifficulty;
import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ChestOpenHandler {

    private static final int   MAX_DISPLAYS    = 10;
    private static final float DISPLAY_SPACING = 2.2f;

    public static final Set<String> excludedPositions = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChestBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!LimboDifficulty.isEnabled(LimboDifficulty.Feature.CHESTS)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Player player = event.getPlayer();
        if (player == null || player.isCreative()) return;

        BlockPos   pos   = event.getPos();
        BlockState state = serverLevel.getBlockState(pos);

        if (!(state.getBlock() instanceof ChestBlock)) return;
        if (!(serverLevel.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)) return;

        CompoundTag nbt = container.serializeNBT();
        if (!nbt.contains("LootTable")) return;

        container.unpackLootTable(player);

        Map<Item, ItemStack> unique = new LinkedHashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            unique.merge(stack.getItem(), stack.copy(),
                    (a, b) -> { a.grow(b.getCount()); return a; });
        }

        container.clearContent();

        if (unique.isEmpty()) return;

        String key = serverLevel.dimension().location() + ":" + pos.toShortString();
        excludedPositions.add(key);

        List<ItemStack> drops = new ArrayList<>(unique.values());
        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        int canSpawn = Math.min(drops.size(), MAX_DISPLAYS - existing);
        if (canSpawn <= 0) return;

        double cx     = pos.getX() + 0.5;
        double cy     = pos.getY() + 1.5;
        double cz     = pos.getZ() + 0.5;
        float  yaw    = player.getYRot();
        double yawRad = Math.toRadians(yaw);
        double perpX  =  Math.cos(yawRad);
        double perpZ  = -Math.sin(yawRad);

        for (int i = 0; i < canSpawn; i++) {
            double offset = (i - (drops.size() - 1) / 2.0) * DISPLAY_SPACING;
            long   seed   = ThreadLocalRandom.current().nextLong();
            LimboDisplayEntity display = new LimboDisplayEntity(
                    serverLevel, drops.get(i), seed,
                    cx + perpX * offset, cy, cz + perpZ * offset, yaw);
            serverLevel.addFreshEntity(display);
        }

        serverLevel.playSound(null, pos, LimboSounds.pickLimboMusic(),
                SoundSource.RECORDS, 4.0f, 1.0f);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!LimboDifficulty.isEnabled(LimboDifficulty.Feature.CHESTS)) return;
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

        serverLevel.playSound(null, immutablePos, LimboSounds.pickLimboMusic(),
                SoundSource.RECORDS, 4.0f, 1.0f);
    }
}