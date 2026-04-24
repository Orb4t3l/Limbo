package com.orbital.limbomod.renderer;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.orbital.limbomod.animation.AnimPhase;
import com.orbital.limbomod.animation.ShuffleAnimator;
import com.orbital.limbomod.animation.SlotState;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class LimboDisplayRenderer extends EntityRenderer<LimboDisplayEntity> {

    private static final ResourceLocation EMPTY_TEXTURE =
            new ResourceLocation("limbomod", "textures/entity/empty.png");

    private static final float ITEM_SCALE = 0.45f;
    private static final float HIT_RADIUS = 0.26f;

    public LimboDisplayRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(LimboDisplayEntity entity, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int light) {

        if (!entity.isInitialized()) return;
        ShuffleAnimator anim = entity.getAnimator();
        if (anim.getPhase() == AnimPhase.DONE) return;
        ItemStack item = entity.getDisplayItem();
        if (item.isEmpty()) return;

        boolean isOutlinePass = buffers instanceof OutlineBufferSource;

        updateHover(entity, anim);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-(entity.getFacingYaw() + 180f)));
        pose.mulPose(Axis.ZP.rotationDegrees(-anim.groupRotation));

        Minecraft mc = Minecraft.getInstance();

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = anim.slots[i];

            if (isOutlinePass) {
                // Render every slot in the outline pass so the outline framebuffer
                // is fully populated — skipping slots causes the Sobel shader to
                // bleed outlines into the gaps and makes items flash/disappear.
                // Non-glowing slots get color (0,0,0,0): invisible but depth-present.
                OutlineBufferSource obs = (OutlineBufferSource) buffers;
                if (slot.glowGreenAlpha > 0.001f) {
                    obs.setColor(50, 255, 80, 255);
                } else if (slot.flashRedAlpha > 0.001f) {
                    obs.setColor(255, 50, 50, 255);
                } else {
                    obs.setColor(0, 0, 0, 0);
                }
            }

            pose.pushPose();
            pose.translate(slot.x, slot.y, i * 0.001f);
            pose.scale(slot.scale * ITEM_SCALE, slot.scale * ITEM_SCALE, slot.scale * ITEM_SCALE);
            mc.getItemRenderer().renderStatic(
                    item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, entity.level(), entity.getId() + i);
            pose.popPose();
        }

        // Hover outline — normal pass only, WAITING phase only
        if (!isOutlinePass && anim.getPhase() == AnimPhase.WAITING) {
            if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch();

            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
                SlotState slot = anim.slots[i];
                if (slot.hoverAlpha < 0.001f) continue;

                pose.pushPose();
                pose.translate(slot.x, slot.y, 0.05f + i * 0.001f);

                float    h  = 0.25f * slot.scale;
                float    a  = slot.hoverAlpha * 0.45f;
                Matrix4f m  = pose.last().pose();
                Matrix3f nm = pose.last().normal();

                line(lines, m, nm, -h, -h,  h, -h, 1f, 1f, 1f, a);
                line(lines, m, nm,  h, -h,  h,  h, 1f, 1f, 1f, a);
                line(lines, m, nm,  h,  h, -h,  h, 1f, 1f, 1f, a);
                line(lines, m, nm, -h,  h, -h, -h, 1f, 1f, 1f, a);

                pose.popPose();
            }
            if (buffers instanceof MultiBufferSource.BufferSource bs)
                bs.endBatch(RenderType.lines());
        }

        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f nm,
                             float x0, float y0, float x1, float y1,
                             float r, float g, float b, float a) {
        vc.vertex(m, x0, y0, 0f).color(r, g, b, a).normal(nm, 0f, 0f, 1f).endVertex();
        vc.vertex(m, x1, y1, 0f).color(r, g, b, a).normal(nm, 0f, 0f, 1f).endVertex();
    }

    private void updateHover(LimboDisplayEntity entity, ShuffleAnimator anim) {
        if (anim.getPhase() != AnimPhase.WAITING) {
            for (SlotState s : anim.slots) s.hoverAlpha = 0f;
            if (ClientHoverState.hoveredEntityId == entity.getId()) ClientHoverState.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 eyePos  = mc.player.getEyePosition(1.0f);
        Vec3 lookVec = mc.player.getLookAngle();

        // Exact same rotation the renderer applies — guaranteed world-space match.
        Matrix4f rot = new Matrix4f()
                .rotateY((float) Math.toRadians(-(entity.getFacingYaw() + 180f)))
                .rotateZ((float) Math.toRadians(-anim.groupRotation));

        Vec3   entityPos = entity.position();
        int    bestSlot  = -1;
        double bestDist  = HIT_RADIUS;

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = anim.slots[i];

            // slot.x / slot.y are the raw translation values the renderer uses —
            // do NOT multiply by scale here; scale only affects item size, not center.
            Vector4f local = new Vector4f(slot.x, slot.y, 0f, 1f);
            rot.transform(local);
            Vec3 slotWorld = entityPos.add(local.x, local.y, local.z);

            Vec3   toSlot = slotWorld.subtract(eyePos);
            double dot    = toSlot.dot(lookVec);
            if (dot < 0 || dot > 12) { slot.hoverAlpha *= 0.7f; continue; }

            // Scale hit radius with item scale so smaller items are proportionally harder to hit
            double scaledHit = HIT_RADIUS * slot.scale;
            double dist = eyePos.add(lookVec.scale(dot)).distanceTo(slotWorld);
            if (dist < scaledHit && dist < bestDist) { bestDist = dist; bestSlot = i; }
        }

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            if (i == bestSlot) anim.slots[i].hoverAlpha = Math.min(1f, anim.slots[i].hoverAlpha + 0.15f);
            else               anim.slots[i].hoverAlpha = Math.max(0f, anim.slots[i].hoverAlpha - 0.10f);
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