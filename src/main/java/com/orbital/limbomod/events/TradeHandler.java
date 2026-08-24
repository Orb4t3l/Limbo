package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboDifficulty;
import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TradeHandler {

    private static final int MAX_DISPLAYS = 10;

    private final Map<UUID, TradeListener> activeListeners = new HashMap<>();

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!LimboDifficulty.isEnabled(LimboDifficulty.Feature.TRADING)) return;
        if (!(event.getContainer() instanceof MerchantMenu menu)) return;
        TradeListener listener = new TradeListener(player, this);
        menu.addSlotListener(listener);
        activeListeners.put(player.getUUID(), listener);
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        activeListeners.remove(event.getEntity().getUUID());
    }

    void onTradeItemAcquired(ServerPlayer player, ItemStack item) {
        if (player.isCreative()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (item.isEmpty()) return;

        int existing = serverLevel.getEntities(LimboEntities.LIMBO_DISPLAY.get(), e -> true).size();
        if (existing >= MAX_DISPLAYS) return;

        float  yaw    = player.getYRot();
        double yawRad = Math.toRadians(yaw);
        double spawnX = player.getX() - Math.sin(yawRad) * 1.5;
        double spawnY = player.getY() + 1.2;
        double spawnZ = player.getZ() + Math.cos(yawRad) * 1.5;

        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(
                serverLevel, item, seed, spawnX, spawnY, spawnZ, yaw);
        serverLevel.addFreshEntity(display);

        serverLevel.playSound(null, player.blockPosition(),
                LimboSounds.pickLimboMusic(), SoundSource.RECORDS, 4.0f, 1.0f);
    }

    private static class TradeListener implements ContainerListener {

        private final ServerPlayer player;
        private final TradeHandler handler;

        // What was in slot 2 last broadcast
        private ItemStack lastResult = ItemStack.EMPTY;
        // Snapshot of every inventory slot last broadcast (slots 3-38)
        private final ItemStack[] lastInventory = new ItemStack[36];
        // Item we are waiting to intercept from inventory
        private ItemStack pendingResult = ItemStack.EMPTY;

        TradeListener(ServerPlayer player, TradeHandler handler) {
            this.player  = player;
            this.handler = handler;
            for (int i = 0; i < lastInventory.length; i++)
                lastInventory[i] = ItemStack.EMPTY;
        }

        @Override
        public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
            if (slot == 2) {
                handleResultSlot(stack);
            } else if (slot >= 3) {
                handleInventorySlot(slot - 3, stack);
            }
        }

        private void handleResultSlot(ItemStack current) {
            if (lastResult.isEmpty() && !current.isEmpty()) {
                // Result appeared — save it so we can intercept it wherever it ends up
                pendingResult = current.copy();
            }

            if (!lastResult.isEmpty() && current.isEmpty() && !pendingResult.isEmpty()) {
                // Result was taken — try carried slot first (normal click)
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty() && ItemStack.isSameItemSameTags(carried, pendingResult)) {
                    ItemStack intercept = carried.copy();
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                    handler.onTradeItemAcquired(player, intercept);
                    pendingResult = ItemStack.EMPTY;
                }
                // If it went to inventory instead, handleInventorySlot will catch it
            }

            lastResult = current.isEmpty() ? ItemStack.EMPTY : current.copy();
        }

        private void handleInventorySlot(int index, ItemStack current) {
            if (index < 0 || index >= lastInventory.length) return;

            ItemStack previous = lastInventory[index];

            // If pendingResult is set, check if this slot received it
            if (!pendingResult.isEmpty()
                    && !current.isEmpty()
                    && ItemStack.isSameItemSameTags(current, pendingResult)) {

                int gained = current.getCount()
                        - (previous.isEmpty() || !ItemStack.isSameItemSameTags(previous, current)
                        ? 0 : previous.getCount());

                if (gained > 0) {
                    ItemStack intercept = pendingResult.copy();
                    intercept.setCount(gained);

                    // Remove from inventory
                    ItemStack live = player.getInventory().getItem(
                            inventoryIndexFromContainerSlot(index));
                    if (!live.isEmpty()) live.shrink(gained);

                    handler.onTradeItemAcquired(player, intercept);
                    pendingResult = ItemStack.EMPTY;
                }
            }

            lastInventory[index] = current.isEmpty() ? ItemStack.EMPTY : current.copy();
        }

        private int inventoryIndexFromContainerSlot(int index) {
            if (index < 27) return index + 9;  // main rows
            return index - 27;                  // hotbar
        }

        @Override
        public void dataChanged(AbstractContainerMenu menu, int id, int value) {}
    }
}