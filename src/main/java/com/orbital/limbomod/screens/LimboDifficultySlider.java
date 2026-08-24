package com.orbital.limbomod.screens;

import com.orbital.limbomod.LimboDifficulty;
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
        setMessage(Component.literal("Limbo Difficulty: " + tier + "/12"));
    }

    @Override
    protected void applyValue() {
        int tier = 1 + (int) Math.round(value * 11);
        LimboDifficulty.setLevel(tier);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        super.renderWidget(g, mx, my, pt);

        int tier = 1 + (int) Math.round(value * 11);
        LimboDifficulty.Feature unlocked = LimboDifficulty.featureUnlockedAt(tier);

        g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                Component.literal(prettyName(unlocked)),
                getX() + getWidth() / 2, getY() + getHeight() + 4, 0xFFFFFF);
    }

    private String prettyName(LimboDifficulty.Feature f) {
        return switch (f) {
            case BLOCK_DROPS        -> "Block Drops";
            case MOB_DROPS          -> "Mob Drops";
            case CRAFTING           -> "Crafting";
            case FISHING            -> "Fishing";
            case ANIMAL_PRODUCTS    -> "Milking & Shearing";
            case BUCKETS            -> "Buckets";
            case TRADING            -> "Villager Trading";
            case CHESTS             -> "Chest Opening";
            case FALLING_BLOCKS     -> "Falling Blocks";
            case EXPLOSIONS         -> "Explosions";
            case DEATH_RESPAWN      -> "Death & Respawn";
            case UNIVERSAL_CATCHALL -> "Everything Else";
        };
    }
}