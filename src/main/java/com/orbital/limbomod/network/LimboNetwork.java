package com.orbital.limbomod.network;

import com.orbital.limbomod.LimboMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class LimboNetwork {

    private static final String PROTOCOL = "1";

    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(LimboMod.MOD_ID, "main"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals
        );

        CHANNEL.registerMessage(
                0,
                SelectItemPacket.class,
                SelectItemPacket::encode,
                SelectItemPacket::decode,
                SelectItemPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }
}