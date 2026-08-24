package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboDifficulty;
import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FishingHandler {

    private static final int   MAX_DISPLAYS    = 10;
    private static final float DISPLAY_SPACING = 2.2f;

    @SubscribeEvent
    public void onFishing(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!LimboDifficulty.isEnabled(LimboDifficulty.Feature.FISHING)) return;
        if (player.isCreative()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        List<ItemStack> drops = event.getDrops().stream()
                .filter(s -> !s.isEmpty())
                .toList();

        if (drops.isEmpty()) return;

        event.getDrops().clear();
        event.setCanceled(true);

        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        int canSpawn = Math.min(drops.size(), MAX_DISPLAYS - existing);
        if (canSpawn <= 0) return;

        float  yaw    = player.getYRot();
        double yawRad = Math.toRadians(yaw);
        double perpX  =  Math.cos(yawRad);
        double perpZ  = -Math.sin(yawRad);
        double spawnX = player.getX() - Math.sin(yawRad) * 1.5;
        double spawnY = player.getY() + 1.2;
        double spawnZ = player.getZ() + Math.cos(yawRad) * 1.5;

        for (int i = 0; i < canSpawn; i++) {
            double offset = (i - (drops.size() - 1) / 2.0) * DISPLAY_SPACING;
            long   seed   = ThreadLocalRandom.current().nextLong();
            LimboDisplayEntity display = new LimboDisplayEntity(
                    serverLevel, drops.get(i), seed,
                    spawnX + perpX * offset,
                    spawnY,
                    spawnZ + perpZ * offset,
                    yaw);
            serverLevel.addFreshEntity(display);
        }

        serverLevel.playSound(null, player.blockPosition(),
                LimboSounds.pickLimboMusic(), SoundSource.RECORDS, 4.0f, 1.0f);
    }
}