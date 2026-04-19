package com.orbital.limbomod.animation;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * Drives the full Limbo key animation sequence.
 *
 * This class is ticked once per game tick (server AND client, same seed → always in sync).
 * The renderer reads {@code slots[]} and {@code groupRotation} every frame.
 *
 * Layout of the 4 × 2 grid (visual slot indices):
 *   [ 0 ][ 1 ][ 2 ][ 3 ]
 *   [ 4 ][ 5 ][ 6 ][ 7 ]
 *
 * All items shown are identical (the correct drop). One random slot is secretly
 * "correct"; it is revealed by the green flash, then hidden by shuffling.
 * The server tracks {@code correctVisualSlot} to validate the player's click.
 */
public class ShuffleAnimator {

    // ── Configuration ─────────────────────────────────────────────────────────
    public  static final int   SLOT_COUNT        = 8;
    private static final int   SWAP_TICKS        = 8;   // ticks for one smooth swap
    private static final int   INTRO_SCALE_TICKS = 22;   // ticks to scale up at center
    private static final int   INTRO_SPLIT_TICKS = 28;   // ticks to spread to grid
    private static final int   FLASH_TICKS       = 48;   // ticks for the green pulses
    private static final float SPACING_X         = 0.55f; // world units between columns
    private static final float SPACING_Y         = 0.55f; // world units between rows

    // ── Grid positions (constant, computed once) ──────────────────────────────
    private final float[] gridX = new float[SLOT_COUNT];
    private final float[] gridY = new float[SLOT_COUNT];

    // ── Per-slot visual state (read by renderer) ──────────────────────────────
    public final SlotState[] slots = new SlotState[SLOT_COUNT];

    // ── Correctness ───────────────────────────────────────────────────────────
    // The visual slot index (0-7) whose item is the real drop.
    // Updated whenever that slot participates in a swap.
    private int correctVisualSlot;

    // ── Group rotation in degrees (read by renderer) ──────────────────────────
    // Applied around Z (the camera-facing axis) in the billboard plane.
    public float groupRotation = 0f;

    // ── Rotation phase bookkeeping ─────────────────────────────────────────────
    private float rotStart;
    private float rotTarget;
    private int   rotDuration;

    // ── Phase control ─────────────────────────────────────────────────────────
    private AnimPhase phase = AnimPhase.INTRO;
    /** Ticks elapsed since the current phase started (reset on every phase change). */
    private int timer = 0;

    // ── Swap queue ─────────────────────────────────────────────────────────────
    // Each entry is { slotIndexA, slotIndexB }.
    private final Queue<int[]> swapQueue = new LinkedList<>();
    private boolean swapping   = false;
    private int     swapA      = -1;
    private int     swapB      = -1;
    private int     swapTimer  = 0;

    // ── Result ────────────────────────────────────────────────────────────────
    private int     clickedSlot     = -1;
    public  boolean resultIsCorrect = false;
    /** Becomes true when DONE is entered; signals the entity to discard. */
    public  boolean isDone          = false;

    // ── Seeded RNG (server + client use identical seed → stay in sync) ─────────
    private final Random rng;

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────────
    public ShuffleAnimator(long seed) {
        this.rng = new Random(seed);

        // Build the 4×2 grid offsets centred at (0, 0).
        for (int i = 0; i < SLOT_COUNT; i++) {
            int col = i % 4;
            int row = i / 4;
            gridX[i] = (col - 1.5f) * SPACING_X;
            gridY[i] = (0.5f - row) * SPACING_Y;
        }

        // All slots start collapsed to the center with scale = 0.
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = new SlotState(0f, 0f);
        }

        // Secretly pick the correct slot — revealed once during FLASH_CORRECT.
        correctVisualSlot = rng.nextInt(SLOT_COUNT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public accessors
    // ─────────────────────────────────────────────────────────────────────────
    public AnimPhase getPhase()            { return phase; }
    public int       getCorrectVisualSlot(){ return correctVisualSlot; }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main tick  (called once per game tick from LimboDisplayEntity.tick())
    // ─────────────────────────────────────────────────────────────────────────
    public void tick() {
        timer++;
        switch (phase) {
            case INTRO         -> tickIntro();
            case FLASH_CORRECT -> tickFlashCorrect();
            case SWAP_1, SWAP_2, SWAP_3, SWAP_4 -> tickSwaps();
            case ROTATE_360, ROTATE_180, ROTATE_90  -> tickRotation();
            case WAITING       -> tickWaiting();
            case RESULT_FLASH  -> tickResultFlash();
            case DONE          -> isDone = true;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INTRO
    //  Phase 1 (ticks 1-INTRO_SCALE_TICKS): scale all 8 slots up from 0 → 1
    //  Phase 2 (ticks +1 to +INTRO_SPLIT_TICKS): lerp from center → grid positions
    // ─────────────────────────────────────────────────────────────────────────
    private void tickIntro() {
        if (timer <= INTRO_SCALE_TICKS) {
            float t = easeOutBack(timer / (float) INTRO_SCALE_TICKS);
            for (SlotState s : slots) s.scale = t;

        } else {
            int splitTick = timer - INTRO_SCALE_TICKS;
            float t = easeOutCubic(Math.min(1f, splitTick / (float) INTRO_SPLIT_TICKS));
            for (int i = 0; i < SLOT_COUNT; i++) {
                slots[i].x = lerp(0f, gridX[i], t);
                slots[i].y = lerp(0f, gridY[i], t);
            }
            if (splitTick >= INTRO_SPLIT_TICKS) {
                // Snap to exact grid and advance
                for (int i = 0; i < SLOT_COUNT; i++) {
                    slots[i].x = gridX[i];
                    slots[i].y = gridY[i];
                    slots[i].scale = 1f;
                }
                enterPhase(AnimPhase.FLASH_CORRECT);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FLASH_CORRECT
    //  Two full green sine pulses over FLASH_TICKS so the player can see the
    //  correct slot before everything starts moving.
    // ─────────────────────────────────────────────────────────────────────────
    private void tickFlashCorrect() {
        float t   = timer / (float) FLASH_TICKS;
        // sin² gives a smooth 0→1→0→1→0 double pulse
        float glow = (float) Math.pow(Math.sin(t * Math.PI * 2), 2);
        slots[correctVisualSlot].glowGreenAlpha = Math.max(0f, glow);

        if (timer >= FLASH_TICKS) {
            slots[correctVisualSlot].glowGreenAlpha = 0f;
            enterSwapPhase(AnimPhase.SWAP_1, 6);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SWAPS  (shared handler for SWAP_1 … SWAP_4)
    //  Pops swap pairs one at a time from the queue, animating each over
    //  SWAP_TICKS with an ease-in-out curve.
    // ─────────────────────────────────────────────────────────────────────────
    private void tickSwaps() {
        // Kick off the next swap if we're not currently mid-swap
        if (!swapping) {
            if (swapQueue.isEmpty()) {
                advanceFromSwapPhase();
                return;
            }
            int[] pair = swapQueue.poll();
            beginSwap(pair[0], pair[1]);
        }

        swapTimer++;
        float t = easeInOutCubic(Math.min(1f, swapTimer / (float) SWAP_TICKS));

        SlotState a = slots[swapA];
        SlotState b = slots[swapB];
        a.x = lerp(a.startX, a.targetX, t);
        a.y = lerp(a.startY, a.targetY, t);
        b.x = lerp(b.startX, b.targetX, t);
        b.y = lerp(b.startY, b.targetY, t);

        if (swapTimer >= SWAP_TICKS) {
            // Snap to exact targets
            a.x = a.targetX;  a.y = a.targetY;
            b.x = b.targetX;  b.y = b.targetY;
            // Keep correctVisualSlot pointing at the right slot
            if      (swapA == correctVisualSlot) correctVisualSlot = swapB;
            else if (swapB == correctVisualSlot) correctVisualSlot = swapA;
            swapping = false;
        }
    }

    private void beginSwap(int a, int b) {
        swapA = a; swapB = b; swapTimer = 0; swapping = true;
        SlotState sa = slots[a], sb = slots[b];
        sa.startX = sa.x;  sa.startY = sa.y;
        sa.targetX = sb.x; sa.targetY = sb.y;
        sb.startX = sb.x;  sb.startY = sb.y;
        sb.targetX = sa.x; sb.targetY = sa.y;
    }

    /** After a swap phase ends, decide which phase comes next. */
    private void advanceFromSwapPhase() {
        switch (phase) {
            case SWAP_1 -> enterRotationPhase(AnimPhase.ROTATE_360, 360f, 60);
            case SWAP_2 -> enterRotationPhase(AnimPhase.ROTATE_180, 180f, 42);
            case SWAP_3 -> enterRotationPhase(AnimPhase.ROTATE_90,   90f, 28);
            case SWAP_4 -> enterPhase(AnimPhase.WAITING);
            default     -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ROTATIONS  (shared handler for ROTATE_360, ROTATE_180, ROTATE_90)
    //  Smoothly rotates groupRotation from rotStart → rotTarget.
    //  During ROTATE_360 an extra mid-point shuffle of 3 instant swaps is done.
    // ─────────────────────────────────────────────────────────────────────────
    private void tickRotation() {
        float t = easeInOutCubic(Math.min(1f, timer / (float) rotDuration));
        groupRotation = lerp(rotStart, rotTarget, t);

        // For the 360 spin, sneak in 3 instant position swaps at the halfway mark
        if (phase == AnimPhase.ROTATE_360 && timer == rotDuration / 2) {
            doInstantSwaps(3);
        }

        if (timer >= rotDuration) {
            groupRotation = rotTarget;   // snap to exact angle
            switch (phase) {
                case ROTATE_360 -> enterSwapPhase(AnimPhase.SWAP_2, 9);
                case ROTATE_180 -> enterSwapPhase(AnimPhase.SWAP_3, 11);
                case ROTATE_90  -> enterSwapPhase(AnimPhase.SWAP_4, 9);
                default         -> {}
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  WAITING
    //  Gentle scale-pulse on all slots so the player knows to click.
    // ─────────────────────────────────────────────────────────────────────────
    private void tickWaiting() {
        float pulse = 0.92f + 0.08f * (float) Math.sin(timer * 0.12f);
        for (SlotState s : slots) s.scale = pulse;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RESULT_FLASH
    //  Clicked slot fades from red → clear over 18 ticks, then DONE.
    // ─────────────────────────────────────────────────────────────────────────
    private void tickResultFlash() {
        float alpha = Math.max(0f, 1f - timer / 18f);
        if (clickedSlot >= 0 && clickedSlot < SLOT_COUNT) {
            slots[clickedSlot].flashRedAlpha = alpha;
        }
        if (timer >= 18) {
            enterPhase(AnimPhase.DONE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  External trigger: player right-clicked slot `slotIndex`
    // ─────────────────────────────────────────────────────────────────────────
    public void onSlotClicked(int slotIndex) {
        if (phase != AnimPhase.WAITING) return;
        clickedSlot     = slotIndex;
        resultIsCorrect = (slotIndex == correctVisualSlot);
        enterPhase(AnimPhase.RESULT_FLASH);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Phase transition helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void enterPhase(AnimPhase next) {
        phase = next;
        timer = 0;
    }

    /** Enter a swap phase and pre-generate the requested number of swap pairs. */
    private void enterSwapPhase(AnimPhase swapPhase, int swapCount) {
        enterPhase(swapPhase);
        swapQueue.clear();
        swapping = false;
        queueSwaps(swapCount);
    }

    /** Enter a rotation phase and record the start/target/duration. */
    private void enterRotationPhase(AnimPhase rotPhase, float degrees, int durationTicks) {
        enterPhase(rotPhase);
        rotStart    = groupRotation;
        rotTarget   = groupRotation + degrees;
        rotDuration = durationTicks;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Swap helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Enqueue `count` random, non-identical swap pairs. */
    private void queueSwaps(int count) {
        for (int i = 0; i < count; i++) {
            int a = rng.nextInt(SLOT_COUNT);
            int b;
            do { b = rng.nextInt(SLOT_COUNT); } while (b == a);
            swapQueue.add(new int[]{a, b});
        }
    }

    /**
     * Swap slot positions instantly (no lerp animation).
     * Used for the mid-rotation shuffle during ROTATE_360.
     */
    private void doInstantSwaps(int count) {
        for (int i = 0; i < count; i++) {
            int a = rng.nextInt(SLOT_COUNT);
            int b;
            do { b = rng.nextInt(SLOT_COUNT); } while (b == a);

            SlotState sa = slots[a], sb = slots[b];
            float tx = sa.x; float ty = sa.y;
            sa.x = sb.x;     sa.y = sb.y;
            sb.x = tx;       sb.y = ty;

            if      (a == correctVisualSlot) correctVisualSlot = b;
            else if (b == correctVisualSlot) correctVisualSlot = a;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Easing functions
    // ─────────────────────────────────────────────────────────────────────────

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Decelerates at the end (smooth arrival). */
    private static float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    /** Accelerates in, decelerates out — best for swaps. */
    private static float easeInOutCubic(float t) {
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /**
     * Overshoots slightly then settles — gives the intro split a springy feel.
     * Based on the standard back easing function.
     */
    private static float easeOutBack(float t) {
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }
}