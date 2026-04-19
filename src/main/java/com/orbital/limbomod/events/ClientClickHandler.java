package com.orbital.limbomod.events;

import com.orbital.limbomod.network.LimboNetwork;
import com.orbital.limbomod.network.SelectItemPacket;
import com.orbital.limbomod.renderer.ClientHoverState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only.  Listens for right-click and, if the player is hovering over a
 * Limbo display slot (as determined by {@link ClientHoverState}), sends a
 * {@link SelectItemPacket} to the server.
 *
 * Register this class on {@code MinecraftForge.EVENT_BUS} during client setup.
 */
@OnlyIn(Dist.CLIENT)
public class ClientClickHandler {

    @SubscribeEvent
    public void onMouseClick(InputEvent.MouseButton.Post event) {
        // Right mouse button, press only
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        if (ClientHoverState.hoveredEntityId < 0) return;

        LimboNetwork.CHANNEL.sendToServer(
                new SelectItemPacket(
                        ClientHoverState.hoveredEntityId,
                        ClientHoverState.hoveredSlotIndex
                )
        );

        // Clear so the player can't spam-click
        ClientHoverState.clear();
    }
}