package com.orbital.limbomod.screens;

import com.orbital.limbomod.animation.AnimPhase;
import com.orbital.limbomod.animation.ShuffleAnimator;
import com.orbital.limbomod.animation.SlotState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class LimboDeathScreen extends Screen {

    private ShuffleAnimator animator;
    private boolean         animStarted = false;
    private boolean         finished    = false;

    private static final float SCALE    = 115f;
    private static final float BTN_W    = 34f;
    private static final float BTN_H    = 11f;

    public LimboDeathScreen() {
        super(Component.translatable("deathScreen.title"));
    }

    @Override
    protected void init() {
        if (!animStarted) {
            addRenderableWidget(Button.builder(
                            Component.translatable("deathScreen.respawn"),
                            b -> startAnimation())
                    .bounds(width / 2 - 100, height / 4 + 72, 200, 20)
                    .build());

            addRenderableWidget(Button.builder(
                            Component.translatable("deathScreen.titleScreen"),
                            b -> goToTitle())
                    .bounds(width / 2 - 100, height / 4 + 96, 200, 20)
                    .build());
        }
    }

    private void startAnimation() {
        animStarted = true;
        clearWidgets();
        animator = new ShuffleAnimator(new Random().nextLong());
    }

    @Override
    public void tick() {
        super.tick();
        if (animator == null || finished) return;

        animator.tick();

        if (animator.isDone) {
            finished = true;
            if (animator.resultIsCorrect) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() != null)
                    mc.getConnection().send(new ServerboundClientCommandPacket(
                            ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                mc.setScreen(null);
            } else {
                goToTitle();
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        if (!animStarted) {
            g.drawCenteredString(font, title, width / 2, height / 4 - 16, 0xFF5555);
            super.render(g, mx, my, pt);
            return;
        }

        if (animator == null) return;

        g.drawCenteredString(font,
                Component.literal("Choose your respawn"),
                width / 2, height / 2 - 85, 0xFFFFFF);

        int    cx   = width  / 2;
        int    cy   = height / 2;
        double rotR = Math.toRadians(-animator.groupRotation);
        float  cosR = (float) Math.cos(rotR);
        float  sinR = (float) Math.sin(rotR);

        boolean waiting = animator.getPhase() == AnimPhase.WAITING;

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = animator.slots[i];

            float rx = slot.x * cosR - slot.y * sinR;
            float ry = slot.x * sinR + slot.y * cosR;

            float sx = cx + rx * SCALE;
            float sy = cy - ry * SCALE;
            float sc = slot.scale;

            float hw = BTN_W * sc;
            float hh = BTN_H * sc;

            int l = (int)(sx - hw), t = (int)(sy - hh);
            int r = (int)(sx + hw), b = (int)(sy + hh);

            boolean hovered = waiting
                    && mx >= l && mx <= r && my >= t && my <= b;

            if (slot.glowGreenAlpha > 0.001f) {
                int a = (int)(slot.glowGreenAlpha * 220) << 24;
                g.fill(l - 3, t - 3, r + 3, b + 3, a | 0x00DD44);
            }
            if (slot.flashRedAlpha > 0.001f) {
                int a = (int)(slot.flashRedAlpha * 220) << 24;
                g.fill(l - 3, t - 3, r + 3, b + 3, a | 0xFF2020);
            }
            if (slot.hoverAlpha > 0.001f) {
                int a = (int)(slot.hoverAlpha * 120) << 24;
                g.fill(l - 1, t - 1, r + 1, b + 1, a | 0xFFFFFF);
            }

            int bgA  = Math.min(255, (int)(sc * 200));
            int bgCol = hovered
                    ? (bgA << 24) | 0x3366AA
                    : (bgA << 24) | 0x333333;
            g.fill(l, t, r, b, bgCol);

            int border = hovered ? 0xFFFFFFFF : 0xFF888888;
            g.fill(l, t, r, t + 1, border);
            g.fill(l, b - 1, r, b, border);
            g.fill(l, t + 1, l + 1, b - 1, border);
            g.fill(r - 1, t + 1, r, b - 1, border);

            if (sc > 0.35f) {
                g.pose().pushPose();
                g.pose().translate(sx, sy, 0);
                g.pose().scale(sc * 0.75f, sc * 0.75f, 1f);
                g.drawCenteredString(font,
                        Component.translatable("deathScreen.respawn"),
                        0, -font.lineHeight / 2, 0xFFFFFF);
                g.pose().popPose();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (animator == null || animator.getPhase() != AnimPhase.WAITING)
            return super.mouseClicked(mx, my, btn);

        int    cx   = width  / 2;
        int    cy   = height / 2;
        double rotR = Math.toRadians(-animator.groupRotation);
        float  cosR = (float) Math.cos(rotR);
        float  sinR = (float) Math.sin(rotR);

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = animator.slots[i];

            float rx = slot.x * cosR - slot.y * sinR;
            float ry = slot.x * sinR + slot.y * cosR;
            float sx = cx + rx * SCALE;
            float sy = cy - ry * SCALE;
            float hw = BTN_W * slot.scale;
            float hh = BTN_H * slot.scale;

            if (mx >= sx - hw && mx <= sx + hw && my >= sy - hh && my <= sy + hh) {
                animator.onSlotClicked(i);
                return true;
            }
        }
        return false;
    }

    private void goToTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.disconnect();
        mc.clearLevel();
        mc.setScreen(new TitleScreen());
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen()    { return false; }
}