package com.orbital.limbomod.renderer;

import com.orbital.limbomod.animation.AnimPhase;
import com.orbital.limbomod.animation.ShuffleAnimator;
import com.orbital.limbomod.animation.SlotState;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LimboDisplayRenderer extends EntityRenderer<LimboDisplayEntity> {

    private static final ResourceLocation EMPTY_TEXTURE =
            new ResourceLocation("limbomod", "textures/entity/empty.png");

    private static final float HIT_RADIUS     = 0.28f;
    private static final float ITEM_SCALE     = 0.45f;
    // How far the border sits from the item center (just outside the item)
    private static final float BORDER_INNER   = 0.26f;
    // Thickness of each border bar
    private static final float BORDER_THICK   = 0.045f;

    public LimboDisplayRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(LimboDisplayEntity entity, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int light) {

        if (!entity.isInitialized()) return;
        ShuffleAnimator anim = entity.getAnimator();
        if (anim.getPhase() == AnimPhase.DONE) return;
        ItemStack item = entity.getDisplayItem();
        if (item.isEmpty()) return;

        Minecraft mc     = Minecraft.getInstance();
        Camera    camera = mc.gameRenderer.getMainCamera();

        updateHover(entity, anim, camera);

        pose.pushPose();
        pose.mulPose(camera.rotation());
        pose.mulPose(Axis.ZP.rotationDegrees(-anim.groupRotation));

        VertexConsumer outlineVerts = buffers.getBuffer(RenderType.debugFilledBox());

        // Pass 1: border outlines behind items
        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot  = anim.slots[i];
            float     zOff  = -0.02f + i * 0.001f; // slightly behind item layer

            boolean needsBorder = slot.glowGreenAlpha > 0.001f
                    || slot.flashRedAlpha  > 0.001f
                    || slot.hoverAlpha     > 0.001f;
            if (!needsBorder) continue;

            pose.pushPose();
            pose.translate(slot.x, slot.y, zOff);

            // Scale border with the slot so it tracks during intro/waiting pulse
            float bs = slot.scale;
            Matrix4f m = pose.last().pose();

            if (slot.glowGreenAlpha > 0.001f)
                addBorder(outlineVerts, m, bs, 0.10f, 1.00f, 0.20f, slot.glowGreenAlpha);
            if (slot.flashRedAlpha > 0.001f)
                addBorder(outlineVerts, m, bs, 1.00f, 0.10f, 0.10f, slot.flashRedAlpha);
            if (slot.hoverAlpha > 0.001f)
                addBorder(outlineVerts, m, bs, 1.00f, 1.00f, 1.00f, slot.hoverAlpha * 0.6f);

            pose.popPose();
        }

        // Flush outlines before items so items sit cleanly on top
        if (buffers instanceof MultiBufferSource.BufferSource bs)
            bs.endBatch(RenderType.debugFilledBox());

        // Pass 2: items
        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = anim.slots[i];
            pose.pushPose();
            pose.translate(slot.x, slot.y, i * 0.0002f);
            pose.scale(slot.scale * ITEM_SCALE, slot.scale * ITEM_SCALE, slot.scale * ITEM_SCALE);
            mc.getItemRenderer().renderStatic(
                    item, ItemDisplayContext.GROUND,
                    light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, entity.level(), entity.getId() + i);
            pose.popPose();
        }

        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /**
     * Draws 4 thin rectangles forming a square border frame around the slot.
     * Each bar goes: bottom, top, left, right.
     * The frame sits just outside BORDER_INNER, with thickness BORDER_THICK.
     * Everything is scaled by `scale` so it matches the item's current size.
     */
    private static void addBorder(VertexConsumer vc, Matrix4f m, float scale,
                                  float r, float g, float b, float a) {
        float inner = BORDER_INNER * scale;
        float outer = inner + BORDER_THICK * scale;

        // Bottom
        addRect(vc, m, -outer, -outer,  outer, -inner, r, g, b, a);
        // Top
        addRect(vc, m, -outer,  inner,  outer,  outer, r, g, b, a);
        // Left
        addRect(vc, m, -outer, -inner, -inner,  inner, r, g, b, a);
        // Right
        addRect(vc, m,  inner, -inner,  outer,  inner, r, g, b, a);
    }

    private static void addRect(VertexConsumer vc, Matrix4f m,
                                float x0, float y0, float x1, float y1,
                                float r, float g, float b, float a) {
        vc.vertex(m, x0, y0, 0f).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y0, 0f).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y1, 0f).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y1, 0f).color(r, g, b, a).endVertex();
    }

    private void updateHover(LimboDisplayEntity entity, ShuffleAnimator anim, Camera camera) {
        if (anim.getPhase() != AnimPhase.WAITING) {
            for (SlotState s : anim.slots) s.hoverAlpha = 0f;
            ClientHoverState.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eyePos  = mc.player.getEyePosition(1.0f);
        Vec3 lookVec = mc.player.getLookAngle();

        Quaternionf camRot = camera.rotation();
        Vector3f r3 = camRot.transform(new Vector3f(1, 0, 0));
        Vector3f u3 = camRot.transform(new Vector3f(0, 1, 0));
        Vec3 right = new Vec3(r3.x, r3.y, r3.z);
        Vec3 up    = new Vec3(u3.x, u3.y, u3.z);

        double rotRad = Math.toRadians(-anim.groupRotation);
        double cosR = Math.cos(rotRad), sinR = Math.sin(rotRad);
        Vec3 rotRight = right.scale(cosR).add(up.scale(-sinR));
        Vec3 rotUp    = right.scale(sinR).add(up.scale( cosR));

        Vec3   entityPos = entity.position();
        int    bestSlot  = -1;
        double bestDot   = -1;

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot      = anim.slots[i];
            Vec3      slotWorld = entityPos
                    .add(rotRight.scale(slot.x * slot.scale * ITEM_SCALE))
                    .add(rotUp.scale(   slot.y * slot.scale * ITEM_SCALE));
            Vec3   toSlot    = slotWorld.subtract(eyePos);
            double dot       = toSlot.dot(lookVec);
            if (dot < 0 || dot > 10) { slot.hoverAlpha *= 0.7f; continue; }
            Vec3   projected = eyePos.add(lookVec.scale(dot));
            double dist      = projected.distanceTo(slotWorld);
            if (dist < HIT_RADIUS && dot > bestDot) { bestDot = dot; bestSlot = i; }
        }

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            if (i == bestSlot) anim.slots[i].hoverAlpha = Math.min(1f, anim.slots[i].hoverAlpha + 0.2f);
            else               anim.slots[i].hoverAlpha = Math.max(0f, anim.slots[i].hoverAlpha - 0.15f);
        }

        if (bestSlot >= 0) {
            ClientHoverState.hoveredEntityId  = entity.getId();
            ClientHoverState.hoveredSlotIndex = bestSlot;
        } else if (ClientHoverState.hoveredEntityId == entity.getId()) {
            ClientHoverState.clear();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(LimboDisplayEntity entity) { return EMPTY_TEXTURE; }
}