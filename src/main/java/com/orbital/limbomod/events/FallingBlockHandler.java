package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FallingBlockHandler {

    private static final int   MAX_DISPLAYS    = 10;
    private static final float DISPLAY_SPACING = 2.2f;

    private final Set<String>              managedPositions = new HashSet<>();
    private final Map<String, List<ItemStack>> pendingDrops = new LinkedHashMap<>();

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof FallingBlockEntity falling)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = BlockPos.containing(falling.position());
        String key = posKey(serverLevel, pos);
        managedPositions.add(key);
        pendingDrops.computeIfAbsent(key, k -> new ArrayList<>());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        BlockPos pos = BlockPos.containing(itemEntity.position());
        String key = posKey(event.getLevel(), pos);

        if (!managedPositions.contains(key)) return;

        event.setCanceled(true);
        ItemStack drop = itemEntity.getItem().copy();
        if (!drop.isEmpty()) {
            pendingDrops.computeIfAbsent(key, k -> new ArrayList<>()).add(drop);
        }
    }

    @SubscribeEvent
    public void onLevelTickEnd(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        for (Map.Entry<String, List<ItemStack>> entry : pendingDrops.entrySet()) {
            List<ItemStack> drops = entry.getValue();
            if (drops.isEmpty()) continue;

            String[] parts = entry.getKey().split(":");
            if (parts.length < 2) continue;
            String[] coords = parts[1].split(",");
            if (coords.length < 3) continue;

            double cx, cy, cz;
            try {
                cx = Double.parseDouble(coords[0].trim()) + 0.5;
                cy = Double.parseDouble(coords[1].trim()) + 1.5;
                cz = Double.parseDouble(coords[2].trim()) + 0.5;
            } catch (NumberFormatException e) {
                continue;
            }

            int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
            int canSpawn = Math.min(drops.size(), MAX_DISPLAYS - existing);
            if (canSpawn <= 0) continue;

            double perpX =  1.0;
            double perpZ =  0.0;

            for (int i = 0; i < canSpawn; i++) {
                double offset = (i - (drops.size() - 1) / 2.0) * DISPLAY_SPACING;
                long seed = ThreadLocalRandom.current().nextLong();
                LimboDisplayEntity display = new LimboDisplayEntity(
                        serverLevel, drops.get(i), seed,
                        cx + perpX * offset, cy, cz + perpZ * offset, 0f);
                serverLevel.addFreshEntity(display);
            }

            BlockPos soundPos = BlockPos.containing(cx, cy, cz);
            serverLevel.playSound(null, soundPos,
                    LimboSounds.LIMBO_MUSIC.get(), SoundSource.RECORDS, 4.0f, 1.0f);
        }

        pendingDrops.clear();
        managedPositions.clear();
    }

    private static String posKey(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel sl)
            return sl.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return "unknown:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}