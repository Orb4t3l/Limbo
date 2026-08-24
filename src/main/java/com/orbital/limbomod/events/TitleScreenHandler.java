package com.orbital.limbomod.events;

import com.orbital.limbomod.LimboDifficulty;
import com.orbital.limbomod.screens.LimboDifficultySlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class TitleScreenHandler {

    private static final int ICON_SIZE   = 18;
    private static final int ICON_GAP    = 3;
    private static final int LEFT_MARGIN = 16;

    private static LimboDifficultySlider slider;

    private static final String[] DESCRIPTIONS = {
            "Break a block, gotta pick the right one to keep it.",
            "Kill something, its loot goes into the grid too.",
            "Crafting something? Better hope you pick right.",
            "Reel in a catch and it's up for grabs now.",
            "Milking, shearing, eggs... none of it's free anymore.",
            "Scooping water or lava means playing for it first.",
            "Villager trades aren't a sure thing anymore.",
            "Loot chests get shuffled before they open.",
            "Blocks that fall and break drop into the grid.",
            "Explosions scatter their drops into the grid too.",
            "Die and even respawning is a gamble now.",
            "Everything else that could possibly drop, does this too."
    };

    @SubscribeEvent
    public void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        slider = new LimboDifficultySlider(LEFT_MARGIN, 66, 90, 14);
        event.addListener(slider);
    }

    @SubscribeEvent
    public void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        if (slider == null) return;

        GuiGraphics g   = event.getGuiGraphics();
        Minecraft   mc  = Minecraft.getInstance();
        int         current = LimboDifficulty.getLevel();
        int         mx = event.getMouseX();
        int         my = event.getMouseY();

        int rowY = slider.getY() + slider.getHeight() + 6;
        int x    = slider.getX();

        int hoveredTier = -1;

        for (int i = 1; i <= 12; i++) {
            int ix = x + (i - 1) * (ICON_SIZE + ICON_GAP);

            boolean active   = i <= current;
            boolean selected = i == current;
            boolean hovered  = mx >= ix && mx <= ix + ICON_SIZE && my >= rowY && my <= rowY + ICON_SIZE;
            if (hovered) hoveredTier = i;

            ResourceLocation icon = new ResourceLocation(
                    "limbomod", "textures/gui/difficulty_" + i + ".png");

            if (selected) {
                long  now   = System.currentTimeMillis();
                float pulse = (float) (0.5 + 0.5 * Math.sin(now / 220.0));
                int   glowA = (int) (70 + pulse * 90);
                int   ring  = (glowA << 24) | 0xFFEE33;
                int   pad   = 2;
                g.fill(ix - pad, rowY - pad, ix + ICON_SIZE + pad, rowY - pad + 1, ring);
                g.fill(ix - pad, rowY + ICON_SIZE + pad - 1, ix + ICON_SIZE + pad, rowY + ICON_SIZE + pad, ring);
                g.fill(ix - pad, rowY - pad, ix - pad + 1, rowY + ICON_SIZE + pad, ring);
                g.fill(ix + ICON_SIZE + pad - 1, rowY - pad, ix + ICON_SIZE + pad, rowY + ICON_SIZE + pad, ring);
            } else if (hovered) {
                g.fill(ix - 1, rowY - 1, ix + ICON_SIZE + 1, rowY, 0xFFAAAAAA);
                g.fill(ix - 1, rowY + ICON_SIZE, ix + ICON_SIZE + 1, rowY + ICON_SIZE + 1, 0xFFAAAAAA);
                g.fill(ix - 1, rowY - 1, ix, rowY + ICON_SIZE + 1, 0xFFAAAAAA);
                g.fill(ix + ICON_SIZE, rowY - 1, ix + ICON_SIZE + 1, rowY + ICON_SIZE + 1, 0xFFAAAAAA);
            }

            float alphaF = active ? 1.0f : 0.3f;
            g.setColor(1f, 1f, 1f, alphaF);
            g.blit(icon, ix, rowY, ICON_SIZE, ICON_SIZE, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            g.setColor(1f, 1f, 1f, 1f);
        }

        int tierToShow = hoveredTier != -1 ? hoveredTier : current;
        String desc = DESCRIPTIONS[tierToShow - 1];

        int textW  = mc.font.width(desc);
        int boxW   = textW + 12;
        int boxX   = slider.getX();
        int boxY   = slider.getY() - 60;

        g.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + 26, 0xCC0A0A0A);
        g.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY - 1, 0xFF3A3A3A);
        g.fill(boxX - 2, boxY + 25, boxX + boxW + 2, boxY + 26, 0xFF000000);

        String header = "Tier " + tierToShow + (hoveredTier != -1 && hoveredTier != current ? " (preview)" : "");
        g.drawString(mc.font, header, boxX + 4, boxY + 2, 0xFFEE33, false);
        g.drawString(mc.font, desc, boxX + 4, boxY + 13, 0xDDDDDD, false);
    }
}