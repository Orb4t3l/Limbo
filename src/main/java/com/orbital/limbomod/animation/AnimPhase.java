package com.orbital.limbomod.animation;

/**
 * Every state the Limbo display animation passes through, in order.
 *
 * INTRO        → single item appears at center, scales up, then splits to 8 slots
 * FLASH_CORRECT→ the correct slot pulses green twice so the player can see it
 * SWAP_1       → 6 smooth slot-pair swaps
 * ROTATE_360   → full 360° spin around the center (+ 3 instant mid-swaps)
 * SWAP_2       → 9 smooth swaps
 * ROTATE_180   → 180° spin (grid ends upside-down)
 * SWAP_3       → 11 smooth swaps
 * ROTATE_90    → 90° spin
 * SWAP_4       → 9 final swaps
 * WAITING      → frozen, gently pulsing, awaiting right-click
 * RESULT_FLASH → clicked slot flashes red, then entity removes itself
 * DONE         → signals the entity to discard itself
 */
public enum AnimPhase {
    INTRO,
    FLASH_CORRECT,
    SWAP_1,
    ROTATE_360,
    SWAP_2,
    ROTATE_180,
    SWAP_3,
    ROTATE_90,
    SWAP_4,
    WAITING,
    RESULT_FLASH,
    DONE
}