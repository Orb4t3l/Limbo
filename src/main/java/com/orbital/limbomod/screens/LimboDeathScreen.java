package com.orbital.limbomod.screens;

import com.orbital.limbomod.LimboSounds;
import com.orbital.limbomod.animation.AnimPhase;
import com.orbital.limbomod.animation.ShuffleAnimator;
import com.orbital.limbomod.animation.SlotState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class LimboDeathScreen extends DeathScreen {

    private ShuffleAnimator animator;
    private boolean         animStarted  = false;
    private boolean         finished     = false;
    private boolean         musicStarted = false;

    private Button   respawnButton;
    private Button   titleButton;
    private Button[] slotButtons;

    private static final float SCALE = 100f;
    private static final int   BTN_W = 60;
    private static final int   BTN_H = 20;

    public LimboDeathScreen(Component deathMessage, boolean hardcore) {
        super(deathMessage, hardcore);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        respawnButton = Button.builder(
                        Component.translatable("deathScreen.respawn"),
                        b -> startAnimation())
                .bounds(width / 2 - 100, height / 4 + 72, 200, 20)
                .build();

        titleButton = Button.builder(
                        Component.translatable("deathScreen.titleScreen"),
                        b -> goToTitle())
                .bounds(width / 2 - 100, height / 4 + 96, 200, 20)
                .build();

        addRenderableWidget(respawnButton);
        addRenderableWidget(titleButton);

        slotButtons = new Button[ShuffleAnimator.SLOT_COUNT];
        for (int i = 0; i < slotButtons.length; i++) {
            slotButtons[i] = Button.builder(
                            Component.translatable("deathScreen.respawn"),
                            b -> {})
                    .bounds(0, 0, BTN_W, BTN_H)
                    .build();
            slotButtons[i].visible = false;
        }

        if (animStarted) {
            respawnButton.visible = false;
            respawnButton.active  = false;
        }
    }

    private void startAnimation() {
        animStarted = true;
        respawnButton.visible = false;
        respawnButton.active  = false;
        animator = new ShuffleAnimator(new Random().nextLong());
    }

    @Override
    public void tick() {
        if (!musicStarted) {
            musicStarted = true;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(LimboSounds.LIMBO_MUSIC.get(), 1.0f));
        }

        if (animator == null || finished) return;

        animator.tick();

        if (animator.isDone) {
            finished = true;
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().stop(LimboSounds.LIMBO_MUSIC.get().getLocation(), SoundSource.MASTER);

            if (animator.resultIsCorrect) {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundClientCommandPacket(
                            ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                }
                mc.setScreen(null);
            } else {
                goToTitle();
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);

        if (!animStarted || animator == null) return;

        g.drawCenteredString(font,
                Component.literal("Choose your respawn"),
                width / 2, height / 4 + 60, 0xFFFFFFFF);

        int    cx   = width  / 2;
        int    cy   = height / 2 + 15;
        double rotR = Math.toRadians(-animator.groupRotation);
        float  cosR = (float) Math.cos(rotR);
        float  sinR = (float) Math.sin(rotR);

        boolean waiting = animator.getPhase() == AnimPhase.WAITING;

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = animator.slots[i];
            Button    btn  = slotButtons[i];

            float rx = slot.x * cosR - slot.y * sinR;
            float ry = slot.x * sinR + slot.y * cosR;

            float sx = cx + rx * SCALE;
            float sy = cy - ry * SCALE;
            float sc = slot.scale;

            int w = Math.max(1, (int) (BTN_W * sc));
            int h = Math.max(1, (int) (BTN_H * sc));
            int l = (int) (sx - w / 2f);
            int t = (int) (sy - h / 2f);

            btn.setX(l);
            btn.setY(t);
            btn.setWidth(w);
            btn.setHeight(h);
            btn.visible = sc > 0.05f;

            boolean hovered = waiting
                    && mx >= l && mx <= l + w && my >= t && my <= t + h;

            if (slot.glowGreenAlpha > 0.001f) {
                int a = (int) (slot.glowGreenAlpha * 220) << 24;
                g.fill(l - 3, t - 3, l + w + 3, t + h + 3, a | 0x00DD44);
            }
            if (slot.flashRedAlpha > 0.001f) {
                int a = (int) (slot.flashRedAlpha * 220) << 24;
                g.fill(l - 3, t - 3, l + w + 3, t + h + 3, a | 0xFF2020);
            }

            if (btn.visible) {
                btn.render(g, hovered ? l + w / 2 : -9999, hovered ? t + h / 2 : -9999, pt);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (animator != null && animator.getPhase() == AnimPhase.WAITING) {
            int    cx   = width  / 2;
            int    cy   = height / 2 + 15;
            double rotR = Math.toRadians(-animator.groupRotation);
            float  cosR = (float) Math.cos(rotR);
            float  sinR = (float) Math.sin(rotR);

            for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
                SlotState slot = animator.slots[i];
                float rx = slot.x * cosR - slot.y * sinR;
                float ry = slot.x * sinR + slot.y * cosR;
                float sx = cx + rx * SCALE;
                float sy = cy - ry * SCALE;
                int   w  = Math.max(1, (int) (BTN_W * slot.scale));
                int   h  = Math.max(1, (int) (BTN_H * slot.scale));
                int   l  = (int) (sx - w / 2f);
                int   t  = (int) (sy - h / 2f);

                if (mx >= l && mx <= l + w && my >= t && my <= t + h) {
                    animator.onSlotClicked(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void goToTitle() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().stop(LimboSounds.LIMBO_MUSIC.get().getLocation(), SoundSource.MASTER);
        if (mc.level != null) mc.level.disconnect();
        mc.clearLevel();
        mc.setScreen(new TitleScreen());
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen()    { return false; }
}