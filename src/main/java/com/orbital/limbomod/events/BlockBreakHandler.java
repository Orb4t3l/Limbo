package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboDifficulty;
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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BlockBreakHandler {

    private static final int   MAX_DISPLAYS    = 10;
    private static final float DISPLAY_SPACING = 2.2f;

    private static class PendingBreak {
        final List<ItemStack> drops;
        final float           playerYaw;
        final double          cx, cy, cz;
        PendingBreak(List<ItemStack> drops, float yaw, double cx, double cy, double cz) {
            this.drops = drops; this.playerYaw = yaw;
            this.cx = cx; this.cy = cy; this.cz = cz;
        }
    }

    private final Map<String, PendingBreak> pendingBreaks    = new LinkedHashMap<>();
    private final Set<String>               managedPositions = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        if (player.isCreative()) return;
        if (!LimboDifficulty.isEnabled(LimboDifficulty.Feature.BLOCK_DROPS)) return;

        BlockPos    pos   = event.getPos();
        BlockState  state = serverLevel.getBlockState(pos);
        BlockEntity be    = serverLevel.getBlockEntity(pos);
        ItemStack   tool  = player.getMainHandItem();

        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) return;

        String key = posKey(serverLevel, pos);
        if (ChestOpenHandler.excludedPositions.contains(key)) return;

        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, be, player, tool);
        if (drops.isEmpty()) return;

        managedPositions.add(key);
        pendingBreaks.put(key, new PendingBreak(
                drops, player.getYRot(),
                pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        BlockPos pos = BlockPos.containing(itemEntity.position());
        if (managedPositions.contains(posKey(event.getLevel(), pos))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLevelTickEnd(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        ChestOpenHandler.excludedPositions.clear();

        for (PendingBreak pending : pendingBreaks.values()) {
            int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
            int canSpawn = Math.min(pending.drops.size(), MAX_DISPLAYS - existing);
            if (canSpawn <= 0) continue;

            double yawRad = Math.toRadians(pending.playerYaw);
            double perpX  =  Math.cos(yawRad);
            double perpZ  = -Math.sin(yawRad);

            for (int i = 0; i < canSpawn; i++) {
                double offset = (i - (pending.drops.size() - 1) / 2.0) * DISPLAY_SPACING;
                long   seed   = ThreadLocalRandom.current().nextLong();
                LimboDisplayEntity display = new LimboDisplayEntity(
                        serverLevel, pending.drops.get(i), seed,
                        pending.cx + perpX * offset,
                        pending.cy,
                        pending.cz + perpZ * offset,
                        pending.playerYaw);
                serverLevel.addFreshEntity(display);
            }

            serverLevel.playSound(null,
                    BlockPos.containing(pending.cx, pending.cy, pending.cz),
                    LimboSounds.pickLimboMusic(), SoundSource.RECORDS, 4.0f, 1.0f);
        }

        pendingBreaks.clear();
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