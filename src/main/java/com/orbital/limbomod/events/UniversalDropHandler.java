package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ThreadLocalRandom;

public class UniversalDropHandler {

    private static final int MAX_DISPLAYS = 10;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;
        if (LimboDisplayEntity.isExempt(stack)) return;

        event.setCanceled(true);

        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        if (existing >= MAX_DISPLAYS) return;

        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(
                serverLevel, stack.copy(), seed,
                itemEntity.getX(), itemEntity.getY() + 1.5, itemEntity.getZ(), 0f);
        serverLevel.addFreshEntity(display);

        serverLevel.playSound(null, BlockPos.containing(itemEntity.position()),
                LimboSounds.LIMBO_MUSIC.get(), SoundSource.RECORDS, 4.0f, 1.0f);
    }
}