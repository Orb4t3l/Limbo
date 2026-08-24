package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboDifficulty;
import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ThreadLocalRandom;

public class CraftingHandler {

    private static final int   MAX_DISPLAYS    = 10;
    private static final float DISPLAY_SPACING = 2.2f;

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!LimboDifficulty.isEnabled(LimboDifficulty.Feature.CRAFTING)) return;
        if (player.isCreative()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;

        int count = crafted.getCount();
        ItemStack toSpawn = crafted.copy();

        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && ItemStack.isSameItemSameTags(carried, toSpawn)) {
            int remove = Math.min(count, carried.getCount());
            carried.shrink(remove);
            count = remove;
        } else {
            int remaining = count;
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, toSpawn)) {
                    int take = Math.min(remaining, slot.getCount());
                    slot.shrink(take);
                    remaining -= take;
                }
            }
            count -= remaining;
        }

        if (count <= 0) return;

        toSpawn.setCount(count);

        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        if (existing >= MAX_DISPLAYS) return;

        float  yaw    = player.getYRot();
        double yawRad = Math.toRadians(yaw);
        double spawnX = player.getX() - Math.sin(yawRad) * 1.5;
        double spawnY = player.getY() + 1.2;
        double spawnZ = player.getZ() + Math.cos(yawRad) * 1.5;

        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(
                serverLevel, toSpawn, seed, spawnX, spawnY, spawnZ, yaw);
        serverLevel.addFreshEntity(display);

        serverLevel.playSound(null, player.blockPosition(),
                LimboSounds.pickLimboMusic(), SoundSource.RECORDS, 4.0f, 1.0f);
    }
}