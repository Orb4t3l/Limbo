package com.orbital.limbomod.events;

import com.orbital.limbomod.network.LimboNetwork;
import com.orbital.limbomod.network.SelectItemPacket;
import com.orbital.limbomod.renderer.ClientHoverState;
import com.orbital.limbomod.screens.LimboDeathScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class ClientClickHandler {

    @SubscribeEvent
    public void onMouseClick(InputEvent.MouseButton.Post event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (ClientHoverState.hoveredEntityId < 0) return;

        LimboNetwork.CHANNEL.sendToServer(new SelectItemPacket(
                ClientHoverState.hoveredEntityId,
                ClientHoverState.hoveredSlotIndex));
        ClientHoverState.clear();
    }

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof DeathScreen death && !(death instanceof LimboDeathScreen)) {
            event.setNewScreen(new LimboDeathScreen(
                    death.getTitle(),
                    net.minecraft.client.Minecraft.getInstance().isLocalServer()
                            && net.minecraft.client.Minecraft.getInstance().getSingleplayerServer() != null
                            && net.minecraft.client.Minecraft.getInstance().getSingleplayerServer().isHardcore()));
        }
    }
}