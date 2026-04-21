package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class BlockBreakHandler {

    private static final int MAX_DISPLAYS = 10;

    private final Set<String> managedPositions = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Player player = event.getPlayer();
        if (player == null) return;

        BlockPos    pos   = event.getPos();
        BlockState  state = serverLevel.getBlockState(pos);
        BlockEntity be    = serverLevel.getBlockEntity(pos);
        ItemStack   tool  = player.getMainHandItem();

        List<ItemStack> previewDrops = Block.getDrops(state, serverLevel, pos, be, player, tool);
        if (previewDrops.isEmpty()) return;

        managedPositions.add(posKey(serverLevel, pos));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        Level    level = event.getLevel();
        BlockPos pos   = BlockPos.containing(itemEntity.position());

        if (!managedPositions.contains(posKey(level, pos))) return;

        event.setCanceled(true);

        ItemStack drop = itemEntity.getItem().copy();
        if (drop.isEmpty()) return;

        // Enforce cap — count existing displays in this level
        long existing = ((ServerLevel) level)
                .getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true)
                .size();
        if (existing >= MAX_DISPLAYS) return;

        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(level, drop, seed);
        display.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        ((ServerLevel) level).addFreshEntity(display);

        level.playSound(null, pos, LimboSounds.LIMBO_MUSIC.get(),
                SoundSource.RECORDS, 4.0f, 1.0f);
    }

    @SubscribeEvent
    public void onLevelTickEnd(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide())
            managedPositions.clear();
    }

    private static String posKey(Level level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.toShortString();
    }

    private static String posKey(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (level instanceof Level l) return posKey(l, pos);
        return "unknown:" + pos.toShortString();
    }
}