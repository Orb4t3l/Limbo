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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LimboDisplayRenderer extends EntityRenderer<LimboDisplayEntity> {

    private static final ResourceLocation EMPTY_TEXTURE =
            new ResourceLocation("limbomod", "textures/entity/empty.png");

    private static final float HIT_RADIUS   = 0.28f;
    private static final float ITEM_SCALE   = 0.45f;
    // Where the outline square sits — just outside the item's visual edge
    private static final float OUTLINE_HALF = 0.30f;

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

        // Fixed orientation — rotate around Y to face the direction the player
        // was looking when the block was broken. Does not track the camera.
        pose.mulPose(Axis.YP.rotationDegrees(-(entity.getFacingYaw() + 180f)));

        // Group rotation around Z (the forward axis) for the shuffle spin phases
        pose.mulPose(Axis.ZP.rotationDegrees(-anim.groupRotation));

        // Pass 1: items
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

        // Flush items before drawing outlines on top
        if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch();

        // Pass 2: outlines using RenderType.lines() — no winding order issues,
        // and naturally renders in front of items since we flush first.
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = anim.slots[i];

            boolean needsOutline = slot.glowGreenAlpha > 0.001f
                    || slot.flashRedAlpha  > 0.001f
                    || slot.hoverAlpha     > 0.001f;
            if (!needsOutline) continue;

            pose.pushPose();
            // Sit the outline slightly in front of the item layer
            pose.translate(slot.x, slot.y, 0.04f + i * 0.001f);

            float   sz = OUTLINE_HALF * slot.scale;
            Matrix4f m  = pose.last().pose();
            Matrix3f nm = pose.last().normal();

            if (slot.glowGreenAlpha > 0.001f)
                addOutlineRect(lines, m, nm, sz, 0.10f, 1.00f, 0.20f, slot.glowGreenAlpha);
            if (slot.flashRedAlpha > 0.001f)
                addOutlineRect(lines, m, nm, sz, 1.00f, 0.10f, 0.10f, slot.flashRedAlpha);
            if (slot.hoverAlpha > 0.001f)
                addOutlineRect(lines, m, nm, sz, 1.00f, 1.00f, 1.00f, slot.hoverAlpha * 0.7f);

            pose.popPose();
        }

        // Flush lines
        if (buffers instanceof MultiBufferSource.BufferSource bs)
            bs.endBatch(RenderType.lines());

        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /**
     * Draws a square outline (4 line segments) centred at the current pose origin.
     * Uses RenderType.lines() which requires POSITION_COLOR_NORMAL vertices.
     * We draw each edge 3 times at slightly different sizes so it reads as a
     * thick border rather than a hairline — this mimics how the vanilla glow
     * outline looks at distance.
     */
    private static void addOutlineRect(VertexConsumer vc, Matrix4f m, Matrix3f nm,
                                       float half, float r, float g, float b, float a) {
        for (int pass = 0; pass < 3; pass++) {
            float h = half + pass * 0.012f;
            // Bottom edge
            line(vc, m, nm, -h, -h,  h, -h, r, g, b, a);
            // Top edge
            line(vc, m, nm, -h,  h,  h,  h, r, g, b, a);
            // Left edge
            line(vc, m, nm, -h, -h, -h,  h, r, g, b, a);
            // Right edge
            line(vc, m, nm,  h, -h,  h,  h, r, g, b, a);
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f nm,
                             float x0, float y0, float x1, float y1,
                             float r, float g, float b, float a) {
        vc.vertex(m, x0, y0, 0f).color(r, g, b, a).normal(nm, 0f, 0f, 1f).endVertex();
        vc.vertex(m, x1, y1, 0f).color(r, g, b, a).normal(nm, 0f, 0f, 1f).endVertex();
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

        // Reconstruct the panel's right and up axes from the stored fixed yaw
        float   yawRad   = (float) Math.toRadians(entity.getFacingYaw() + 180f);
        Vector3f fwd     = new Vector3f((float) Math.sin(yawRad), 0f, -(float) Math.cos(yawRad));
        Vector3f worldUp = new Vector3f(0f, 1f, 0f);
        Vector3f right3  = new Vector3f(fwd).cross(worldUp).normalize();
        Vec3 right = new Vec3(right3.x, right3.y, right3.z);
        Vec3 up    = new Vec3(0, 1, 0);

        // Apply group rotation to right/up
        double rotRad = Math.toRadians(-anim.groupRotation);
        double cosR = Math.cos(rotRad), sinR = Math.sin(rotRad);
        Vec3 rotRight = right.scale(cosR).add(up.scale(-sinR));
        Vec3 rotUp    = right.scale(sinR).add(up.scale( cosR));

        Vec3  entityPos = entity.position();
        int   bestSlot  = -1;
        double bestDot  = -1;

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