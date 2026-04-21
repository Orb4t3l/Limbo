package com.orbital.limbomod.renderer;

import com.orbital.limbomod.animation.AnimPhase;
import com.orbital.limbomod.animation.ShuffleAnimator;
import com.orbital.limbomod.animation.SlotState;
import com.orbital.limbomod.entity.LimboDisplayEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
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

    private static final float HIT_RADIUS  = 0.28f;
    private static final float GLOW_HALF   = 0.32f; // size of the glow quad in world units, independent of item scale
    private static final float ITEM_SCALE  = 0.45f;

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

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = anim.slots[i];

            pose.pushPose();
            pose.translate(slot.x, slot.y, i * 0.0002f);

            // Flush batched draws before any direct Tesselator calls
            if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch();

            // Glow quads rendered at their own fixed size so they're always visible
            if (slot.glowGreenAlpha > 0.001f)
                renderColoredQuad(pose, GLOW_HALF * slot.scale, 0.10f, 1.00f, 0.20f, slot.glowGreenAlpha * 0.92f);
            if (slot.flashRedAlpha > 0.001f)
                renderColoredQuad(pose, GLOW_HALF * slot.scale, 1.00f, 0.10f, 0.10f, slot.flashRedAlpha  * 0.85f);
            if (slot.hoverAlpha > 0.001f)
                renderColoredQuad(pose, GLOW_HALF * slot.scale, 1.00f, 1.00f, 1.00f, slot.hoverAlpha     * 0.30f);

            // Item rendered at its own scale on top
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

    private static void renderColoredQuad(PoseStack pose, float half, float r, float g, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f      m    = pose.last().pose();
        Tesselator    tess = Tesselator.getInstance();
        BufferBuilder buf  = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(m, -half, -half, 0f).color(r, g, b, a).endVertex();
        buf.vertex(m,  half, -half, 0f).color(r, g, b, a).endVertex();
        buf.vertex(m,  half,  half, 0f).color(r, g, b, a).endVertex();
        buf.vertex(m, -half,  half, 0f).color(r, g, b, a).endVertex();
        tess.end();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    @Override
    public ResourceLocation getTextureLocation(LimboDisplayEntity entity) { return EMPTY_TEXTURE; }
}