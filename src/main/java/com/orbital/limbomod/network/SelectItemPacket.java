package com.orbital.limbomod.network;

import com.orbital.limbomod.entity.LimboDisplayEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from the client to the server when the player right-clicks a slot
 * on a {@link LimboDisplayEntity}.
 *
 * Fields
 *   entityId  – the entity's network ID (from {@code Entity.getId()})
 *   slotIndex – visual slot index 0-7 that was clicked
 */
public class SelectItemPacket {

    private final int entityId;
    private final int slotIndex;

    public SelectItemPacket(int entityId, int slotIndex) {
        this.entityId  = entityId;
        this.slotIndex = slotIndex;
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    public static void encode(SelectItemPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.entityId);
        buf.writeByte(pkt.slotIndex);
    }

    public static SelectItemPacket decode(FriendlyByteBuf buf) {
        return new SelectItemPacket(buf.readVarInt(), buf.readByte());
    }

    // ── Handler (runs on the SERVER networking thread, must be dispatched) ────

    public static void handle(SelectItemPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(pkt.entityId);
            if (entity instanceof LimboDisplayEntity display) {
                // Basic sanity: player must be within 10 blocks
                if (display.distanceTo(player) > 10.0) return;
                display.onPlayerClickSlot(pkt.slotIndex, player);
            }
        });
        ctx.setPacketHandled(true);
    }
}