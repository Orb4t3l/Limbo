package com.orbital.limbomod.entity;

import com.orbital.limbomod.animation.AnimPhase;
import com.orbital.limbomod.animation.ShuffleAnimator;
import com.orbital.limbomod.animation.SlotState;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

public class LimboDisplayEntity extends Entity {

    private static final int MAX_LIFETIME = 600;

    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Long> DATA_SEED =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_FACING_YAW =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CLICKED_SLOT =
            SynchedEntityData.defineId(LimboDisplayEntity.class, EntityDataSerializers.INT);

    private ShuffleAnimator animator;
    private ItemStack        displayItem   = ItemStack.EMPTY;
    private boolean          initialized   = false;
    private int              lifetimeTicks = 0;

    private BlockPos    successPlacePos   = null;
    private BlockState  successPlaceState = null;
    private CompoundTag successPlaceNbt   = null;

    public LimboDisplayEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public LimboDisplayEntity(Level level, ItemStack item, long seed,
                              double x, double y, double z, float facingYaw) {
        this(LimboEntities.LIMBO_DISPLAY.get(), level);
        this.displayItem = item.copy();
        this.entityData.set(DATA_ITEM, item.copy());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_FACING_YAW, facingYaw);
        this.entityData.set(DATA_CLICKED_SLOT, -1);
        this.setPos(x, y, z);
        initAnimator(seed);
    }

    public void setSuccessPlacement(BlockPos pos, BlockState state, CompoundTag nbt) {
        this.successPlacePos   = pos;
        this.successPlaceState = state;
        this.successPlaceNbt   = nbt;
    }

    public void setSuccessPlacement(BlockPos pos, BlockState state) {
        setSuccessPlacement(pos, state, null);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ITEM, ItemStack.EMPTY);
        entityData.define(DATA_SEED, 0L);
        entityData.define(DATA_FACING_YAW, 0f);
        entityData.define(DATA_CLICKED_SLOT, -1);
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
        if (key == DATA_CLICKED_SLOT && level().isClientSide()) {
            int clicked = entityData.get(DATA_CLICKED_SLOT);
            if (clicked >= 0 && animator != null) animator.onSlotClicked(clicked);
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

    @Override
    public int getTeamColor() { return 0xFFFFFF; }

    public void onPlayerClickSlot(int slotIndex, Player player) {
        if (animator == null || animator.getPhase() != AnimPhase.WAITING) return;
        boolean correct = (slotIndex == animator.getCorrectVisualSlot());
        entityData.set(DATA_CLICKED_SLOT, slotIndex);
        animator.onSlotClicked(slotIndex);
        if (correct) {
            if (successPlacePos != null && successPlaceState != null) {
                level().setBlock(successPlacePos, successPlaceState, 3);
                if (successPlaceNbt != null
                        && level().getBlockEntity(successPlacePos) instanceof RandomizableContainerBlockEntity be) {
                    successPlaceNbt.putInt("x", successPlacePos.getX());
                    successPlaceNbt.putInt("y", successPlacePos.getY());
                    successPlaceNbt.putInt("z", successPlacePos.getZ());
                    be.deserializeNBT(successPlaceNbt);
                }
            } else {
                ItemEntity drop = new ItemEntity(level(), getX(), getY(), getZ(), displayItem.copy());
                drop.setDefaultPickUpDelay();
                level().addFreshEntity(drop);
            }
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