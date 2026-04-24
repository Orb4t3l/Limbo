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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BlockBreakHandler {

    private static final int   MAX_DISPLAYS = 10;
    // Horizontal gap between display centers (world units).
    // Each display's grid is ~1.65 units wide; 2.2 gives comfortable clearance.
    private static final float DISPLAY_SPACING = 2.2f;

    // Keyed by posKey. Stores everything needed to spawn displays at end of tick.
    private static class PendingBreak {
        final List<ItemStack> drops;
        final float           playerYaw;
        final double          centerX, centerY, centerZ;

        PendingBreak(List<ItemStack> drops, float yaw, double cx, double cy, double cz) {
            this.drops = drops; this.playerYaw = yaw;
            this.centerX = cx; this.centerY = cy; this.centerZ = cz;
        }
    }

    private final Map<String, PendingBreak> pendingBreaks  = new LinkedHashMap<>();
    private final Set<String>               managedPositions = new HashSet<>();

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

        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, be, player, tool);
        if (drops.isEmpty()) return;

        String key = posKey(serverLevel, pos);
        managedPositions.add(key);
        pendingBreaks.put(key, new PendingBreak(
                drops,
                player.getYRot(),
                pos.getX() + 0.5,
                pos.getY() + 1.5,
                pos.getZ() + 0.5
        ));
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

        for (PendingBreak pending : pendingBreaks.values()) {
            spawnDisplays(serverLevel, pending);
        }

        pendingBreaks.clear();
        managedPositions.clear();
    }

    private static void spawnDisplays(ServerLevel level, PendingBreak pending) {
        List<ItemStack> drops = pending.drops;
        int count = drops.size();

        // Check how many displays already exist — cap the total
        int existing = level.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        int canSpawn = Math.min(count, MAX_DISPLAYS - existing);
        if (canSpawn <= 0) return;

        // Compute the right-hand perpendicular to the player's facing direction
        // so displays spread sideways relative to the player, not into them.
        double yawRad  = Math.toRadians(pending.playerYaw);
        // Perpendicular to yaw in XZ: rotate yaw by 90°
        double perpX   =  Math.cos(yawRad);
        double perpZ   = -Math.sin(yawRad);

        // Total spread width centred on the block position.
        // With N displays, there are N-1 gaps between them.
        // offsetForIndex(i) = (i - (count-1)/2.0) * DISPLAY_SPACING
        for (int i = 0; i < canSpawn; i++) {
            ItemStack drop = drops.get(i);
            if (drop.isEmpty()) continue;

            double offset = (i - (count - 1) / 2.0) * DISPLAY_SPACING;
            double spawnX = pending.centerX + perpX * offset;
            double spawnY = pending.centerY;
            double spawnZ = pending.centerZ + perpZ * offset;

            long seed = ThreadLocalRandom.current().nextLong();
            LimboDisplayEntity display = new LimboDisplayEntity(
                    level, drop, seed, spawnX, spawnY, spawnZ, pending.playerYaw);
            level.addFreshEntity(display);
        }

        // One music cue for the whole break regardless of drop count
        BlockPos pos = BlockPos.containing(pending.centerX, pending.centerY, pending.centerZ);
        level.playSound(null, pos, LimboSounds.LIMBO_MUSIC.get(),
                SoundSource.RECORDS, 4.0f, 1.0f);
    }

    private static String posKey(Level level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.toShortString();
    }

    private static String posKey(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (level instanceof Level l) return posKey(l, pos);
        return "unknown:" + pos.toShortString();
    }
}