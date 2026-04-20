package com.orbital.limbomod;

import com.orbital.limbomod.LimboMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class LimboSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LimboMod.MOD_ID);

    public static final RegistryObject<SoundEvent> LIMBO_MUSIC =
            SOUNDS.register("limbo_music",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(LimboMod.MOD_ID, "limbo_music")));
}