package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.concurrent.ThreadLocalRandom;

public class AnimalProductHandler {

    private static final int MAX_DISPLAYS = 10;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        ItemStack held = player.getItemInHand(event.getHand());

        if ((event.getTarget() instanceof Cow || event.getTarget() instanceof Goat)
                && !((event.getTarget() instanceof Cow c) && c.isBaby())
                && held.is(Items.BUCKET)) {

            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

            held.shrink(1);
            if (held.isEmpty()) player.setItemInHand(event.getHand(), ItemStack.EMPTY);

            spawnDisplayNearPlayer(serverLevel, player, new ItemStack(Items.MILK_BUCKET));
        }

        if (event.getTarget() instanceof Sheep sheep
                && !sheep.isSheared()
                && !sheep.isBaby()
                && held.is(Items.SHEARS)) {

            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

            held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(event.getHand()));
            sheep.setSheared(true);
            sheep.playSound(SoundEvents.SHEEP_SHEAR, 1.0f, 1.0f);

            ResourceLocation woolId = new ResourceLocation("minecraft", sheep.getColor().getName() + "_wool");
            Item woolItem = ForgeRegistries.ITEMS.getValue(woolId);
            if (woolItem == null) woolItem = Items.WHITE_WOOL;

            int count = 1 + serverLevel.random.nextInt(3);
            spawnDisplayNearPlayer(serverLevel, player, new ItemStack(woolItem, count));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        if (!itemEntity.getItem().is(Items.EGG)) return;

        AABB box = new AABB(itemEntity.position(), itemEntity.position()).inflate(2.0);
        if (serverLevel.getEntitiesOfClass(Chicken.class, box).isEmpty()) return;

        event.setCanceled(true);

        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        if (existing >= MAX_DISPLAYS) return;

        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(
                serverLevel, itemEntity.getItem().copy(), seed,
                itemEntity.getX(), itemEntity.getY() + 1.5, itemEntity.getZ(), 0f);
        serverLevel.addFreshEntity(display);

        serverLevel.playSound(null, BlockPos.containing(itemEntity.position()),
                LimboSounds.LIMBO_MUSIC.get(), SoundSource.RECORDS, 4.0f, 1.0f);
    }

    private void spawnDisplayNearPlayer(ServerLevel level, ServerPlayer player, ItemStack item) {
        int existing = level.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        if (existing >= MAX_DISPLAYS) return;

        double yawRad = Math.toRadians(player.getYRot());
        double spawnX = player.getX() - Math.sin(yawRad) * 1.5;
        double spawnY = player.getY() + 1.2;
        double spawnZ = player.getZ() + Math.cos(yawRad) * 1.5;

        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(
                level, item, seed, spawnX, spawnY, spawnZ, player.getYRot());
        level.addFreshEntity(display);

        level.playSound(null, BlockPos.containing(spawnX, spawnY, spawnZ),
                LimboSounds.LIMBO_MUSIC.get(), SoundSource.RECORDS, 4.0f, 1.0f);
    }
}