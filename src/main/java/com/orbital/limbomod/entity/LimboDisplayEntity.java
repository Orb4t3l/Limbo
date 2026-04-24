package com.orbital.limbomod.entity;

import com.orbital.limbomod.animation.AnimPhase;
import com.orbital.limbomod.animation.ShuffleAnimator;
import com.orbital.limbomod.animation.SlotState;
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

public class LimboDisplayEntity extends Entity {

    private static final int MAX_LIFETIME = 600;

    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Long> DATA_SEED =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_FACING_YAW =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.FLOAT);

    private ShuffleAnimator animator;
    private ItemStack        displayItem   = ItemStack.EMPTY;
    private boolean          initialized   = false;
    private int              lifetimeTicks = 0;

    public LimboDisplayEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public LimboDisplayEntity(Level level, ItemStack item, long seed, float facingYaw) {
        this(LimboEntities.LIMBO_DISPLAY.get(), level);
        this.displayItem = item.copy();
        this.entityData.set(DATA_ITEM, item.copy());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_FACING_YAW, facingYaw);
        initAnimator(seed);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ITEM, ItemStack.EMPTY);
        entityData.define(DATA_SEED, 0L);
        entityData.define(DATA_FACING_YAW, 0f);
    }

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

    @Override
    public void tick() {
        super.tick();
        if (!initialized) return;
        lifetimeTicks++;
        if (lifetimeTicks >= MAX_LIFETIME) { this.discard(); return; }
        animator.tick();
        if (animator.isDone) this.discard();
    }

    @Override
    public boolean isCurrentlyGlowing() {
        if (animator == null) return false;
        for (SlotState s : animator.slots)
            if (s.glowGreenAlpha > 0.001f || s.flashRedAlpha > 0.001f) return true;
        return false;
    }

    // Returning white means the level renderer won't tint or override the
    // per-slot colors we set via obs.setColor() inside the renderer.
    @Override
    public int getTeamColor() { return 0xFFFFFF; }

    public void onPlayerClickSlot(int slotIndex, Player player) {
        if (animator == null || animator.getPhase() != AnimPhase.WAITING) return;
        boolean correct = (slotIndex == animator.getCorrectVisualSlot());
        animator.onSlotClicked(slotIndex);
        if (correct) {
            ItemEntity drop = new ItemEntity(level(), getX(), getY(), getZ(), displayItem.copy());
            drop.setDefaultPickUpDelay();
            level().addFreshEntity(drop);
        } else {
            player.hurt(damageSources().generic(), 0.5f);
        }
    }

    public float           getFacingYaw()  { return entityData.get(DATA_FACING_YAW); }
    public ShuffleAnimator getAnimator()   { return animator;    }
    public ItemStack       getDisplayItem(){ return displayItem; }
    public boolean         isInitialized() { return initialized; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("DisplayItem")) {
            displayItem = ItemStack.of(tag.getCompound("DisplayItem"));
            entityData.set(DATA_ITEM, displayItem.copy());
        }
        long seed = tag.getLong("AnimSeed");
        entityData.set(DATA_SEED, seed);
        entityData.set(DATA_FACING_YAW, tag.getFloat("FacingYaw"));
        if (seed != 0L && !displayItem.isEmpty()) initAnimator(seed);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!displayItem.isEmpty()) {
            CompoundTag itemTag = new CompoundTag();
            displayItem.save(itemTag);
            tag.put("DisplayItem", itemTag);
        }
        tag.putLong("AnimSeed", entityData.get(DATA_SEED));
        tag.putFloat("FacingYaw", entityData.get(DATA_FACING_YAW));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public boolean isNoGravity()  { return true;  }
    @Override public boolean isPushable()   { return false; }
    @Override public boolean isPickable()   { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double dist) { return dist < 4096; }
}