package com.orbital.limbomod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.concurrent.ThreadLocalRandom;

public class LimboSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LimboMod.MOD_ID);

    public static final RegistryObject<SoundEvent> LIMBO_MUSIC =
            SOUNDS.register("limbo_music",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(LimboMod.MOD_ID, "limbo_music")));

    public static final RegistryObject<SoundEvent> LIMBO_MUSIC_2 =
            SOUNDS.register("limbo_music2",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(LimboMod.MOD_ID, "limbo_music2")));

    public static SoundEvent pickLimboMusic() {
        return ThreadLocalRandom.current().nextInt(100) == 0
                ? LIMBO_MUSIC_2.get()
                : LIMBO_MUSIC.get();
    }
}