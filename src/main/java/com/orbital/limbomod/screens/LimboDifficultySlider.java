package com.orbital.limbomod.screens;

import com.orbital.limbomod.LimboDifficulty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LimboDifficultySlider extends AbstractSliderButton {

    public LimboDifficultySlider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
                (LimboDifficulty.getLevel() - 1) / 11.0);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        int tier = 1 + (int) Math.round(value * 11);
        setMessage(Component.literal("Tier " + tier + " / 12"));
    }

    @Override
    protected void applyValue() {
        int tier = 1 + (int) Math.round(value * 11);
        LimboDifficulty.setLevel(tier);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x80000000);
        g.fill(x, y, x + w, y + h, 0xFF2B2B2B);
        g.fill(x, y, x + w, y + 1, 0xFF555555);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF0A0A0A);

        int fillW = (int) (w * value);
        int grad1 = 0xFF44BB55;
        int grad2 = 0xFF2E8B3A;
        for (int i = 0; i < fillW; i++) {
            float t = w == 0 ? 0 : (float) i / w;
            int col = lerpColor(grad1, grad2, t);
            g.fill(x + i, y + 2, x + i + 1, y + h - 2, col);
        }

        int handleX = x + fillW;
        boolean hovered = mx >= handleX - 4 && mx <= handleX + 4 && my >= y && my <= y + h;
        int handleCol = hovered || isFocused() ? 0xFFFFFFFF : 0xFFDDDDDD;
        g.fill(handleX - 2, y - 2, handleX + 2, y + h + 2, 0xFF111111);
        g.fill(handleX - 1, y - 1, handleX + 1, y + h + 1, handleCol);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int gg = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }
}