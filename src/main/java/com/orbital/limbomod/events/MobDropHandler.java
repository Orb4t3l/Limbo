package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboDifficulty;
import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MobDropHandler {

    private static final int   MAX_DISPLAYS    = 10;
    private static final float DISPLAY_SPACING = 2.2f;

    @SubscribeEvent
    public void onMobDrops(LivingDropsEvent event) {
        System.out.println("[LimboMod] onMobDrops fired for: " + event.getEntity().getType().toShortString());
        if (!LimboDifficulty.isEnabled(LimboDifficulty.Feature.MOB_DROPS)) return;
        if (event.getEntity().level().isClientSide()) {
            System.out.println("[LimboMod] Skipping — client side");
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            System.out.println("[LimboMod] Skipping — not a ServerLevel");
            return;
        }
        if (event.getDrops().isEmpty()) {
            System.out.println("[LimboMod] Skipping — drops list is empty");
            return;
        }

        System.out.println("[LimboMod] Drop count: " + event.getDrops().size());

        Player player = null;
        if (event.getSource().getEntity() instanceof Player p) player = p;

        System.out.println("[LimboMod] Killed by player: " + (player != null) + ", creative: " + (player != null && player.isCreative()));

        if (player != null && player.isCreative()) return;

        List<ItemStack> drops = event.getDrops().stream()
                .map(ItemEntity::getItem)
                .filter(s -> !s.isEmpty())
                .toList();

        System.out.println("[LimboMod] Valid drops after filter: " + drops.size());
        drops.forEach(d -> System.out.println("[LimboMod]   - " + d.getDisplayName().getString() + " x" + d.getCount()));

        if (drops.isEmpty()) return;

        event.getDrops().clear();
        System.out.println("[LimboMod] Drops list cleared, size now: " + event.getDrops().size());

        double spawnX = event.getEntity().getX();
        double spawnY = event.getEntity().getY() + 1.0;
        double spawnZ = event.getEntity().getZ();
        float  yaw    = player != null ? player.getYRot() : event.getEntity().getYRot();

        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        int canSpawn = Math.min(drops.size(), MAX_DISPLAYS - existing);

        System.out.println("[LimboMod] Existing displays: " + existing + ", canSpawn: " + canSpawn);

        if (canSpawn <= 0) return;

        double yawRad = Math.toRadians(yaw);
        double perpX  =  Math.cos(yawRad);
        double perpZ  = -Math.sin(yawRad);

        for (int i = 0; i < canSpawn; i++) {
            double offset = (i - (drops.size() - 1) / 2.0) * DISPLAY_SPACING;
            long   seed   = ThreadLocalRandom.current().nextLong();
            LimboDisplayEntity display = new LimboDisplayEntity(
                    serverLevel, drops.get(i), seed,
                    spawnX + perpX * offset,
                    spawnY,
                    spawnZ + perpZ * offset,
                    yaw);
            boolean added = serverLevel.addFreshEntity(display);
            System.out.println("[LimboMod] Spawned display for " + drops.get(i).getDisplayName().getString() + " — addFreshEntity returned: " + added);
        }

        serverLevel.playSound(null, event.getEntity().blockPosition(),
                LimboSounds.LIMBO_MUSIC.get(), SoundSource.RECORDS, 4.0f, 1.0f);
        System.out.println("[LimboMod] Done.");
    }
}