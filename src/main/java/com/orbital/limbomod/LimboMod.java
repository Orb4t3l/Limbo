package com.orbital.limbomod;

import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.orbital.limbomod.entity.LimboEntities;
import com.orbital.limbomod.events.BlockBreakHandler;
import com.orbital.limbomod.events.ClientClickHandler;
import com.orbital.limbomod.events.CraftingHandler;
import com.orbital.limbomod.events.MobDropHandler;
import com.orbital.limbomod.network.LimboNetwork;
import com.orbital.limbomod.renderer.LimboDisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(LimboMod.MOD_ID)
public class LimboMod {

    public static final String MOD_ID = "limbomod";

    public LimboMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        LimboEntities.ENTITIES.register(modBus);
        LimboSounds.SOUNDS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(new BlockBreakHandler());
        MinecraftForge.EVENT_BUS.register(new MobDropHandler());
        MinecraftForge.EVENT_BUS.register(new CraftingHandler());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new ClientClickHandler());
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(LimboNetwork::register);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                EntityRenderers.register(
                        LimboEntities.LIMBO_DISPLAY.get(),
                        LimboDisplayRenderer::new));
    }
}