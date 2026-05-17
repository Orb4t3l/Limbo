package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ExplosionDropHandler {

    private static final int   MAX_DISPLAYS    = 10;
    private static final float DISPLAY_SPACING = 2.2f;

    private static class ExplosionGroup {
        final Vec3              center;
        final Set<String>       blockKeys = new HashSet<>();
        final List<ItemStack>   drops     = new ArrayList<>();

        ExplosionGroup(Vec3 center) { this.center = center; }
    }

    private final List<ExplosionGroup>  activeGroups = new ArrayList<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getAffectedBlocks().isEmpty()) return;

        String dim = event.getLevel().dimension().location().toString();
        Vec3 center = event.getExplosion().getPosition();
        ExplosionGroup group = new ExplosionGroup(center);

        for (BlockPos pos : event.getAffectedBlocks()) {
            group.blockKeys.add(dim + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ());
        }

        activeGroups.add(group);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (activeGroups.isEmpty()) return;

        BlockPos pos = BlockPos.containing(itemEntity.position());
        String dim   = event.getLevel().dimension().location().toString();
        String key   = dim + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();

        for (ExplosionGroup group : activeGroups) {
            if (group.blockKeys.contains(key)) {
                event.setCanceled(true);
                ItemStack drop = itemEntity.getItem().copy();
                if (!drop.isEmpty()) group.drops.add(drop);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onLevelTickEnd(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        for (ExplosionGroup group : activeGroups) {
            if (group.drops.isEmpty()) continue;

            int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
            int canSpawn = Math.min(group.drops.size(), MAX_DISPLAYS - existing);
            if (canSpawn <= 0) continue;

            Vec3 center = group.center;
            for (int i = 0; i < canSpawn; i++) {
                double offset = (i - (group.drops.size() - 1) / 2.0) * DISPLAY_SPACING;
                long   seed   = ThreadLocalRandom.current().nextLong();
                LimboDisplayEntity display = new LimboDisplayEntity(
                        serverLevel, group.drops.get(i), seed,
                        center.x + offset,
                        center.y + 1.5,
                        center.z,
                        0f);
                serverLevel.addFreshEntity(display);
            }

            serverLevel.playSound(null,
                    BlockPos.containing(center.x, center.y, center.z),
                    LimboSounds.LIMBO_MUSIC.get(), SoundSource.RECORDS, 4.0f, 1.0f);
        }

        activeGroups.clear();
    }
}