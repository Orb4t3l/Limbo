package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboDifficulty;
import com.orbital.limbomod.screens.LimboDifficultySlider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class TitleScreenHandler {

    private static final int ICON_SIZE = 20;
    private static final int ICON_GAP  = 4;

    private static LimboDifficultySlider slider;

    @SubscribeEvent
    public void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;

        int sliderX = 20;
        int sliderY = event.getScreen().height / 2 - 60;

        slider = new LimboDifficultySlider(sliderX, sliderY, 100, 20);
        event.addListener(slider);
    }

    @SubscribeEvent
    public void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        if (slider == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int currentTier = LimboDifficulty.getLevel();

        int startY = slider.getY() + 30;
        for (int i = 1; i <= 12; i++) {
            int y = startY + (i - 1) * (ICON_SIZE + ICON_GAP);
            int x = slider.getX();

            ResourceLocation icon = new ResourceLocation(
                    "limbomod", "textures/gui/difficulty_" + i + ".png");

            boolean active = i <= currentTier;
            int tint = active ? 0xFFFFFFFF : 0x66FFFFFF;

            g.setColor(
                    ((tint >> 16) & 0xFF) / 255f,
                    ((tint >> 8)  & 0xFF) / 255f,
                    (tint & 0xFF) / 255f,
                    ((tint >> 24) & 0xFF) / 255f);

            g.blit(icon, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

            g.setColor(1f, 1f, 1f, 1f);

            if (i == currentTier) {
                g.fill(x - 2, y - 2, x + ICON_SIZE + 2, y - 1, 0xFFFFFF00);
                g.fill(x - 2, y + ICON_SIZE + 1, x + ICON_SIZE + 2, y + ICON_SIZE + 2, 0xFFFFFF00);
            }
        }
    }
}