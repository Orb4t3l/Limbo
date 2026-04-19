package com.orbital.limbomod.animation;

/**
 * Holds all the visual state for one of the 8 item slots.
 * Positions are relative to the display entity's center in world units.
 * The renderer reads these values every frame.
 */
public class SlotState {

    // ── Current position in the billboard plane (world units from center) ────
    public float x;
    public float y;

    // ── Swap animation bookkeeping ────────────────────────────────────────────
    // Saved when a swap begins; lerped toward target over SWAP_TICKS.
    public float startX;
    public float startY;
    public float targetX;
    public float targetY;

    // ── Scale ────────────────────────────────────────────────────────────────
    // 0 at spawn, rises to 1 during INTRO, then oscillates gently in WAITING.
    public float scale = 0f;

    // ── Green glow (FLASH_CORRECT) ────────────────────────────────────────────
    // 0 = invisible, 1 = fully opaque. Drives a tinted quad rendered behind the item.
    public float glowGreenAlpha = 0f;

    // ── Red flash (RESULT_FLASH) ──────────────────────────────────────────────
    // Fades from 1 → 0 over the result flash duration.
    public float flashRedAlpha = 0f;

    // ── Hover highlight ───────────────────────────────────────────────────────
    // Updated every frame by the client renderer based on player look direction.
    // Drives a subtle white tint so the player knows which slot they're aiming at.
    public float hoverAlpha = 0f;

    // ─────────────────────────────────────────────────────────────────────────
    public SlotState(float x, float y) {
        this.x = x;
        this.y = y;
        this.startX  = x;
        this.startY  = y;
        this.targetX = x;
        this.targetY = y;
    }
}