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

/**
 * Renders a {@link LimboDisplayEntity} as 8 floating item icons in world space.
 *
 * ── Coordinate system ──────────────────────────────────────────────────────
 * The PoseStack arrives already translated to the entity's position.
 * We multiply by the camera rotation (billboard) then apply the group rotation
 * around the Z-axis (camera-facing axis), so the whole grid spins in the
 * plane facing the player.
 *
 * ── What gets rendered per slot ────────────────────────────────────────────
 *   1. Green glow quad   (during FLASH_CORRECT, behind the item)
 *   2. Red flash quad    (during RESULT_FLASH,  behind the item)
 *   3. Hover quad        (white tint when the player aims at this slot)
 *   4. The item itself   (using ItemRenderer.renderStatic)
 *
 * ── Hover detection ────────────────────────────────────────────────────────
 * Every render frame we ray-cast the player's look vector against all 8
 * slot world-positions and write the nearest hit to {@link ClientHoverState}.
 * The click handler reads that state when a right-click fires.
 */
public class LimboDisplayRenderer extends EntityRenderer<LimboDisplayEntity> {

    /** Dummy texture – entity renderers require one but we don't use it. */
    private static final ResourceLocation EMPTY_TEXTURE =
            new ResourceLocation("limbomod", "textures/entity/empty.png");

    /** Radius (world units) used for slot hit-testing. */
    private static final float HIT_RADIUS = 0.28f;

    /** Half-size of the glow/flash quad in world units. */
    private static final float QUAD_HALF  = 0.30f;

    // ── Lazily built translucent POSITION_COLOR render type for colored quads ─
    private static RenderType coloredQuadType() {
        return RenderType.create(
                "limbo_colored_quad",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true, // sort on upload (translucent)
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionColorShader))
                        .setTransparencyState(new RenderType.TransparencyStateShard("translucent_transparency", () -> {
                            RenderSystem.enableBlend();
                            RenderSystem.defaultBlendFunc();
                        }, () -> {
                            RenderSystem.disableBlend();
                        }))
                        .setDepthTestState(new RenderType.DepthTestStateShard("always", 519))
                        .setWriteMaskState(new RenderType.WriteMaskStateShard(true, true))
                        .createCompositeState(false));
    }

    // Cache the type so we don't rebuild it every frame
    private static RenderType COLORED_QUAD_TYPE = null;

    private static RenderType getColoredQuadType() {
        if (COLORED_QUAD_TYPE == null) COLORED_QUAD_TYPE = coloredQuadType();
        return COLORED_QUAD_TYPE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    public LimboDisplayRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main render entry-point
    // ─────────────────────────────────────────────────────────────────────────

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

        // ── Hover detection ──────────────────────────────────────────────────
        updateHover(entity, anim, camera);

        // ── Billboard + group rotation ────────────────────────────────────────
        pose.pushPose();

        // Face the camera (billboard)
        pose.mulPose(camera.rotation());

        // Spin the whole grid in that plane
        pose.mulPose(Axis.ZP.rotationDegrees(-anim.groupRotation));

        // ── Render each slot ─────────────────────────────────────────────────
        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = anim.slots[i];

            pose.pushPose();

            // Move to slot position in the billboard plane
            pose.translate(slot.x, slot.y, (float) i * 0.0002f); // tiny Z-spread avoids z-fighting
            pose.scale(slot.scale * 0.45f, slot.scale * 0.45f, slot.scale * 0.45f);

            // ── Green glow (FLASH_CORRECT) ───────────────────────────────────
            if (slot.glowGreenAlpha > 0.001f) {
                renderColoredQuad(pose, buffers,
                        0.20f, 1.00f, 0.30f, slot.glowGreenAlpha * 0.75f);
            }

            // ── Red flash (RESULT_FLASH) ─────────────────────────────────────
            if (slot.flashRedAlpha > 0.001f) {
                renderColoredQuad(pose, buffers,
                        1.00f, 0.15f, 0.15f, slot.flashRedAlpha * 0.85f);
            }

            // ── Hover highlight ───────────────────────────────────────────────
            if (slot.hoverAlpha > 0.001f) {
                renderColoredQuad(pose, buffers,
                        1.00f, 1.00f, 1.00f, slot.hoverAlpha * 0.25f);
            }

            // ── Item ─────────────────────────────────────────────────────────
            mc.getItemRenderer().renderStatic(
                    item,
                    ItemDisplayContext.GROUND,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    pose,
                    buffers,
                    entity.level(),
                    entity.getId() + i // vary seed per slot so models differ slightly
            );

            pose.popPose();
        }

        pose.popPose();

        // Let the base class handle the name tag (hidden by default since we
        // override getTextureLocation to return a dummy path).
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Hover / ray-cast
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Projects each slot into world space and tests whether the player's look
     * ray passes within HIT_RADIUS of it. The closest hit updates
     * {@link ClientHoverState} and sets that slot's {@code hoverAlpha} to 1.
     * All other slots fade toward 0.
     */
    private void updateHover(LimboDisplayEntity entity, ShuffleAnimator anim, Camera camera) {
        if (anim.getPhase() != AnimPhase.WAITING) {
            // Only allow clicking in WAITING phase — clear hover and exit early
            for (SlotState s : anim.slots) s.hoverAlpha = 0f;
            ClientHoverState.clear();
            return;
        }

        Minecraft mc      = Minecraft.getInstance();
        Vec3      eyePos  = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition(1.0f);
        Vec3      lookVec = mc.player == null ? Vec3.ZERO : mc.player.getLookAngle();

        // Compute the camera's right and up vectors in world space
        // (needed to transform billboard-plane offsets into world space)
        Quaternionf camRot  = camera.rotation();
        Vector3f    right3f = camRot.transform(new Vector3f(1, 0, 0));
        Vector3f    up3f    = camRot.transform(new Vector3f(0, 1, 0));
        Vec3        right   = new Vec3(right3f.x, right3f.y, right3f.z);
        Vec3        up      = new Vec3(up3f.x,    up3f.y,    up3f.z);

        // Apply group rotation to the right/up axes so hover matches rendering
        double rotRad = Math.toRadians(-anim.groupRotation);
        double cosR   = Math.cos(rotRad);
        double sinR   = Math.sin(rotRad);
        Vec3 rotRight = right.scale(cosR).add(up.scale(-sinR));
        Vec3 rotUp    = right.scale(sinR).add(up.scale( cosR));

        Vec3 entityPos = entity.position();

        int   bestSlot = -1;
        double bestDot = -1;

        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            SlotState slot = anim.slots[i];

            // World position of this slot's center
            Vec3 slotWorld = entityPos
                    .add(rotRight.scale(slot.x * slot.scale * 0.45))
                    .add(rotUp.scale(   slot.y * slot.scale * 0.45));

            // Ray–point distance
            Vec3 toSlot    = slotWorld.subtract(eyePos);
            double dot     = toSlot.dot(lookVec);
            if (dot < 0 || dot > 10) {
                // Behind the player or too far
                slot.hoverAlpha *= 0.7f;
                continue;
            }
            Vec3 projected = eyePos.add(lookVec.scale(dot));
            double dist    = projected.distanceTo(slotWorld);

            if (dist < HIT_RADIUS && dot > bestDot) {
                bestDot  = dot;
                bestSlot = i;
            }
        }

        // Update hover alpha on all slots and write to shared state
        for (int i = 0; i < ShuffleAnimator.SLOT_COUNT; i++) {
            if (i == bestSlot) {
                anim.slots[i].hoverAlpha = Math.min(1f, anim.slots[i].hoverAlpha + 0.2f);
            } else {
                anim.slots[i].hoverAlpha = Math.max(0f, anim.slots[i].hoverAlpha - 0.15f);
            }
        }

        if (bestSlot >= 0) {
            ClientHoverState.hoveredEntityId  = entity.getId();
            ClientHoverState.hoveredSlotIndex = bestSlot;
        } else {
            // Check if we were previously hovering this entity; if so, clear
            if (ClientHoverState.hoveredEntityId == entity.getId()) {
                ClientHoverState.clear();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Colored quad helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draws a QUAD_HALF×QUAD_HALF coloured translucent quad centred on the
     * current pose origin in the XY plane.
     *
     * Uses POSITION_COLOR vertices (no UV, no lightmap) with translucent blending
     * and no depth test so glows always show through items.
     */
    private static void renderColoredQuad(PoseStack pose, MultiBufferSource buffers,
                                          float r, float g, float b, float a) {
        VertexConsumer vc     = buffers.getBuffer(getColoredQuadType());
        Matrix4f       matrix = pose.last().pose();
        float          s      = QUAD_HALF;
        int            color  = packColor(r, g, b, a);

        // Quad winding: counter-clockwise when facing +Z
        vc.vertex(matrix, -s, -s, 0f).color(color).endVertex();
        vc.vertex(matrix,  s, -s, 0f).color(color).endVertex();
        vc.vertex(matrix,  s,  s, 0f).color(color).endVertex();
        vc.vertex(matrix, -s,  s, 0f).color(color).endVertex();
    }

    /** Pack RGBA floats (0-1) into a single ARGB int as expected by VertexConsumer.color(int). */
    private static int packColor(float r, float g, float b, float a) {
        int ai = clamp8(a);
        int ri = clamp8(r);
        int gi = clamp8(g);
        int bi = clamp8(b);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private static int clamp8(float v) {
        return (int) Math.min(255, Math.max(0, v * 255));
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ResourceLocation getTextureLocation(LimboDisplayEntity entity) {
        return EMPTY_TEXTURE;
    }
}