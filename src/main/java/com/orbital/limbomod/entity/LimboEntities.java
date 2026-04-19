package com.orbital.limbomod.entity;

import com.orbital.limbomod.LimboMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class LimboEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LimboMod.MOD_ID);

    /**
     * The floating 8-slot display that replaces a block drop.
     *
     * Sized 4×2 blocks so its bounding box roughly covers all 8 item slots.
     * noSummon() prevents it appearing in /summon tab-completion.
     * noSave() means it won't be written to the chunk NBT — if the server
     * restarts mid-puzzle the display simply vanishes (acceptable).
     */
    public static final RegistryObject<EntityType<LimboDisplayEntity>> LIMBO_DISPLAY =
            ENTITIES.register("limbo_display",
                    () -> EntityType.Builder
                            .<LimboDisplayEntity>of(LimboDisplayEntity::new, MobCategory.MISC)
                            .sized(4.0f, 2.0f)
                            .clientTrackingRange(64)
                            .noSummon()
                            .build("limbo_display"));
}