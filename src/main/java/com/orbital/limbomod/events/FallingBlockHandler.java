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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FallingBlockHandler {

    private static final int   MAX_DISPLAYS    = 10;
    private static final float DISPLAY_SPACING = 2.2f;

    private final Map<String, List<ItemStack>> pendingDrops = new LinkedHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        AABB search = new AABB(itemEntity.position(), itemEntity.position()).inflate(2.0);
        List<FallingBlockEntity> nearby = serverLevel.getEntitiesOfClass(
                FallingBlockEntity.class, search);

        if (nearby.isEmpty()) return;

        FallingBlockEntity faller = nearby.get(0);
        BlockPos fp = BlockPos.containing(faller.position());

        event.setCanceled(true);
        ItemStack drop = itemEntity.getItem().copy();
        if (drop.isEmpty()) return;

        String key = serverLevel.dimension().location()
                + ":" + fp.getX() + "," + fp.getY() + "," + fp.getZ();
        pendingDrops.computeIfAbsent(key, k -> new ArrayList<>()).add(drop);
    }

    @SubscribeEvent
    public void onLevelTickEnd(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        if (pendingDrops.isEmpty()) return;

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

            for (int i = 0; i < canSpawn; i++) {
                double offset = (i - (drops.size() - 1) / 2.0) * DISPLAY_SPACING;
                long seed = ThreadLocalRandom.current().nextLong();
                LimboDisplayEntity display = new LimboDisplayEntity(
                        serverLevel, drops.get(i), seed,
                        cx + offset, cy, cz, 0f);
                serverLevel.addFreshEntity(display);
            }

            serverLevel.playSound(null, BlockPos.containing(cx, cy, cz),
                    LimboSounds.LIMBO_MUSIC.get(), SoundSource.RECORDS, 4.0f, 1.0f);
        }

        pendingDrops.clear();
    }
}