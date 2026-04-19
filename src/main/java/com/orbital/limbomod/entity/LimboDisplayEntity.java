package com.orbital.limbomod.entity;

import com.orbital.limbomod.animation.AnimPhase;
import com.orbital.limbomod.animation.ShuffleAnimator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * A purely visual, physics-free entity spawned at a block-break position.
 *
 * ── Lifecycle ──────────────────────────────────────────────────────────────
 * 1. Server creates it with an ItemStack and a random seed.
 * 2. Both server and client tick the same ShuffleAnimator (seeded identically),
 *    so animation state is always consistent between sides.
 * 3. When the player right-clicks a slot (via SelectItemPacket), the server
 *    checks whether that slot is the correct one, then either drops the item
 *    or damages the player.
 * 4. The entity discards itself once the animator reaches AnimPhase.DONE.
 *
 * ── Data syncing ───────────────────────────────────────────────────────────
 * DATA_ITEM and DATA_SEED are synced via SynchedEntityData. The client
 * initialises its ShuffleAnimator as soon as both are available.
 */
public class LimboDisplayEntity extends Entity {

    // ── Synced fields ─────────────────────────────────────────────────────────
    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Long> DATA_SEED =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.LONG);

    // ── Internal state ────────────────────────────────────────────────────────
    private ShuffleAnimator animator;
    private ItemStack        displayItem  = ItemStack.EMPTY;
    private boolean          initialized  = false;

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructors
    // ─────────────────────────────────────────────────────────────────────────

    /** Used by Forge's entity registry when loading from NBT or a spawn packet. */
    public LimboDisplayEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Server-side convenience constructor.
     *
     * @param level  the server level
     * @param item   the real drop the player must win
     * @param seed   random seed; must be passed to the client so animators stay in sync
     */
    public LimboDisplayEntity(Level level, ItemStack item, long seed) {
        this(LimboEntities.LIMBO_DISPLAY.get(), level);
        this.displayItem = item.copy();
        this.entityData.set(DATA_ITEM, item.copy());
        this.entityData.set(DATA_SEED, seed);
        initAnimator(seed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SynchedEntityData
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ITEM, ItemStack.EMPTY);
        entityData.define(DATA_SEED, 0L);
    }

    /**
     * Called on the CLIENT when a synced data value changes.
     * We wait until both DATA_ITEM and DATA_SEED have non-default values
     * before constructing the animator so the client always starts with the
     * correct seed.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);

        if (!initialized) {
            long      seed = entityData.get(DATA_SEED);
            ItemStack item = entityData.get(DATA_ITEM);
            if (seed != 0L && !item.isEmpty()) {
                displayItem = item.copy();
                initAnimator(seed);
            }
        }
    }

    private void initAnimator(long seed) {
        animator    = new ShuffleAnimator(seed);
        initialized = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tick
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!initialized) return;

        animator.tick();

        if (animator.isDone) {
            this.discard();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Click handling  (called by packet handler on the server)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates a slot click from a player.
     * Must be called server-side only.
     *
     * @param slotIndex  the visual slot index (0-7) the player clicked
     * @param player     the player who clicked
     */
    public void onPlayerClickSlot(int slotIndex, Player player) {
        if (animator == null || animator.getPhase() != AnimPhase.WAITING) return;

        boolean correct = (slotIndex == animator.getCorrectVisualSlot());
        // Advance animator state (RESULT_FLASH → DONE) on the server side.
        // The client's animator advances identically because it is ticked from
        // the same entity tick; we only need to trigger it here on the server.
        animator.onSlotClicked(slotIndex);

        if (correct) {
            // Drop the real item at the display's position
            ItemEntity drop = new ItemEntity(
                    level(),
                    getX(), getY(), getZ(),
                    displayItem.copy());
            drop.setDefaultPickUpDelay();
            level().addFreshEntity(drop);
        } else {
            // Punish with half a heart of generic damage
            player.hurt(damageSources().generic(), 0.5f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public getters
    // ─────────────────────────────────────────────────────────────────────────

    public ShuffleAnimator getAnimator()    { return animator;    }
    public ItemStack       getDisplayItem() { return displayItem; }
    public boolean         isInitialized()  { return initialized; }

    // ─────────────────────────────────────────────────────────────────────────
    //  Serialization  (NBT save/load)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("DisplayItem")) {
            displayItem = ItemStack.of(tag.getCompound("DisplayItem"));
            entityData.set(DATA_ITEM, displayItem.copy());
        }
        long seed = tag.getLong("AnimSeed");
        entityData.set(DATA_SEED, seed);

        // Restart the animator from scratch on world reload.
        // The puzzle is lost, but that is acceptable (the entity almost never
        // survives a server restart in normal play).
        if (seed != 0L && !displayItem.isEmpty()) {
            initAnimator(seed);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!displayItem.isEmpty()) {
            CompoundTag itemTag = new CompoundTag();
            displayItem.save(itemTag);
            tag.put("DisplayItem", itemTag);
        }
        tag.putLong("AnimSeed", entityData.get(DATA_SEED));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Misc entity overrides
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        // NetworkHooks sends extra data attached via IEntityAdditionalSpawnData if needed;
        // for now SynchedEntityData handles the item + seed sync automatically.
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public boolean isNoGravity()   { return true;  }
    @Override public boolean isPushable()    { return false; }
    @Override public boolean isPickable()    { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double dist) { return dist < 4096; /* 64 blocks */ }
}