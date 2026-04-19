package com.orbital.limbomod.events;

import com.orbital.limbomod.entity.LimboDisplayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Intercepts block drops and replaces every ItemEntity with a {@link LimboDisplayEntity}.
 *
 * ── How it works ────────────────────────────────────────────────────────────
 * 1. {@link #onBlockBreak} fires BEFORE the block is removed.
 *    We pre-compute the drop list (so we know what should drop) and record the
 *    block position in {@code managedPositions}.
 *
 * 2. {@link #onEntityJoin} fires for every entity entering the level — including
 *    the ItemEntities that vanilla spawns for drops. We cancel any ItemEntity
 *    that appears at a managed position and replace it with a LimboDisplayEntity.
 *
 * 3. {@link #onLevelTickEnd} clears {@code managedPositions} at end-of-tick so
 *    stale entries never accumulate.
 *
 * ── Why intercept in EntityJoinLevelEvent instead of cancelling BreakEvent ──
 * Cancelling BreakEvent prevents the block from being removed, which is not what
 * we want. Intercepting the spawned ItemEntities lets vanilla handle all the
 * book-keeping (experience, block-entity cleanup, etc.) while we simply swap out
 * the resulting drops.
 */
public class BlockBreakHandler {

    /**
     * Key format:  "<dimension_id>:<x>,<y>,<z>"
     * Positions recorded here are cleared at end of each server tick.
     */
    private final Set<String> managedPositions = new HashSet<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Step 1 – record managed positions when a player breaks a block
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        // Server-side only
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        Player player = event.getPlayer();
        if (player == null) return;

        BlockPos    pos   = event.getPos();
        BlockState  state = serverLevel.getBlockState(pos);
        BlockEntity be    = serverLevel.getBlockEntity(pos);
        ItemStack   tool  = player.getMainHandItem();

        // Pre-compute what vanilla would drop so we know whether to intercept at all.
        // (If the block drops nothing — e.g. a glass pane in Survival — skip.)
        List<ItemStack> previewDrops = Block.getDrops(state, serverLevel, pos, be, player, tool);
        if (previewDrops.isEmpty()) return;

        // Register this position; EntityJoinLevelEvent will pick it up this tick.
        managedPositions.add(posKey(serverLevel, pos));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Step 2 – intercept ItemEntity spawns at managed positions
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        // Server-side, ItemEntity only
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        Level    level = event.getLevel();
        BlockPos pos   = BlockPos.containing(itemEntity.position());

        if (!managedPositions.contains(posKey(level, pos))) return;

        // Cancel the vanilla item drop
        event.setCanceled(true);

        ItemStack drop = itemEntity.getItem().copy();
        if (drop.isEmpty()) return;

        // Spawn the Limbo display entity at 1.5 blocks above the break point
        long seed = ThreadLocalRandom.current().nextLong();
        LimboDisplayEntity display = new LimboDisplayEntity(level, drop, seed);
        display.setPos(
                pos.getX() + 0.5,
                pos.getY() + 1.5,
                pos.getZ() + 0.5
        );
        ((ServerLevel) level).addFreshEntity(display);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Step 3 – clear managed positions at the end of each server tick
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onLevelTickEnd(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && !event.level.isClientSide()) {
            managedPositions.clear();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String posKey(Level level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.toShortString();
    }

    // Overload for LevelAccessor (used in BreakEvent where level is a LevelAccessor)
    private static String posKey(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (level instanceof Level l) return posKey(l, pos);
        return "unknown:" + pos.toShortString();
    }
}