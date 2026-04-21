package com.orbital.limbomod.animation;

import java.util.Random;

public class ShuffleAnimator {

    public  static final int   SLOT_COUNT        = 8;
    private static final int   SWAP_TICKS        = 6;
    private static final int   INTRO_SCALE_TICKS = 10;
    private static final int   INTRO_SPLIT_TICKS = 12;
    private static final int   FLASH_TICKS       = 26;
    private static final float SPACING_X         = 0.55f;
    private static final float SPACING_Y         = 0.55f;

    private final float[] gridX = new float[SLOT_COUNT];
    private final float[] gridY = new float[SLOT_COUNT];

    public final SlotState[] slots = new SlotState[SLOT_COUNT];

    private final int correctVisualSlot;

    public float groupRotation = 0f;

    private float rotStart;
    private float rotTarget;
    private int   rotDuration;

    private AnimPhase phase = AnimPhase.INTRO;
    private int timer = 0;

    private boolean swapping  = false;
    private int     swapTimer = 0;
    private int     swapsLeft = 0;

    private int     clickedSlot     = -1;
    public  boolean resultIsCorrect = false;
    public  boolean isDone          = false;

    private final Random rng;

    public ShuffleAnimator(long seed) {
        this.rng = new Random(seed);
        for (int i = 0; i < SLOT_COUNT; i++) {
            int col = i % 4;
            int row = i / 4;
            gridX[i] = (col - 1.5f) * SPACING_X;
            gridY[i] = (0.5f - row) * SPACING_Y;
        }
        for (int i = 0; i < SLOT_COUNT; i++) slots[i] = new SlotState(0f, 0f);
        correctVisualSlot = rng.nextInt(SLOT_COUNT);
    }

    public AnimPhase getPhase()             { return phase; }
    public int       getCorrectVisualSlot() { return correctVisualSlot; }

    public void tick() {
        timer++;
        switch (phase) {
            case INTRO                                    -> tickIntro();
            case FLASH_CORRECT                           -> tickFlashCorrect();
            case SWAP_2, SWAP_3, SWAP_4, SWAP_5         -> tickSwaps();
            case ROTATE_180, ROTATE_90, ROTATE_90_FINAL -> tickRotation();
            case WAITING                                 -> tickWaiting();
            case RESULT_FLASH                            -> tickResultFlash();
            case DONE                                    -> isDone = true;
        }
    }

    private void tickIntro() {
        if (timer <= INTRO_SCALE_TICKS) {
            float t = easeOutBack(timer / (float) INTRO_SCALE_TICKS);
            for (SlotState s : slots) s.scale = Math.max(0f, Math.min(1f, t));
        } else {
            int splitTick = timer - INTRO_SCALE_TICKS;
            float t = easeOutCubic(Math.min(1f, splitTick / (float) INTRO_SPLIT_TICKS));
            for (int i = 0; i < SLOT_COUNT; i++) {
                slots[i].x = lerp(0f, gridX[i], t);
                slots[i].y = lerp(0f, gridY[i], t);
            }
            if (splitTick >= INTRO_SPLIT_TICKS) {
                for (int i = 0; i < SLOT_COUNT; i++) {
                    slots[i].x = gridX[i]; slots[i].y = gridY[i]; slots[i].scale = 1f;
                }
                enterPhase(AnimPhase.FLASH_CORRECT);
            }
        }
    }

    private void tickFlashCorrect() {
        float t    = timer / (float) FLASH_TICKS;
        float glow = (float) Math.pow(Math.sin(t * Math.PI * 2), 2);
        glow = Math.max(0f, glow);
        slots[correctVisualSlot].glowGreenAlpha = glow;
        slots[correctVisualSlot].scale          = 1f + glow * 0.25f;
        if (timer >= FLASH_TICKS) {
            slots[correctVisualSlot].glowGreenAlpha = 0f;
            slots[correctVisualSlot].scale          = 1f;
            enterSwapPhase(AnimPhase.SWAP_2, 9);
        }
    }

    private void tickSwaps() {
        if (!swapping) {
            if (swapsLeft <= 0) { advanceFromSwapPhase(); return; }
            swapsLeft--;
            beginMassSwap();
        }
        swapTimer++;
        float t = easeInOutCubic(Math.min(1f, swapTimer / (float) SWAP_TICKS));
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i].x = lerp(slots[i].startX, slots[i].targetX, t);
            slots[i].y = lerp(slots[i].startY, slots[i].targetY, t);
        }
        if (swapTimer >= SWAP_TICKS) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                slots[i].x = slots[i].targetX; slots[i].y = slots[i].targetY;
            }
            swapping = false;
        }
    }

    private void beginMassSwap() {
        float[] curX = new float[SLOT_COUNT];
        float[] curY = new float[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) { curX[i] = slots[i].x; curY[i] = slots[i].y; }
        int[] perm = {0,1,2,3,4,5,6,7};
        for (int i = SLOT_COUNT - 1; i > 0; i--) {
            int j = rng.nextInt(i);
            int tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i].startX  = slots[i].x;  slots[i].startY  = slots[i].y;
            slots[i].targetX = curX[perm[i]]; slots[i].targetY = curY[perm[i]];
        }
        swapTimer = 0; swapping = true;
    }

    private void advanceFromSwapPhase() {
        switch (phase) {
            case SWAP_2 -> enterRotationPhase(AnimPhase.ROTATE_180,     180f, 10);
            case SWAP_3 -> enterRotationPhase(AnimPhase.ROTATE_90,       90f, 6);
            case SWAP_4 -> enterRotationPhase(AnimPhase.ROTATE_90_FINAL, 90f, 6);
            case SWAP_5 -> enterPhase(AnimPhase.WAITING);
            default     -> {}
        }
    }

    private void tickRotation() {
        float t = easeInOutCubic(Math.min(1f, timer / (float) rotDuration));
        groupRotation = lerp(rotStart, rotTarget, t);
        if (timer >= rotDuration) {
            groupRotation = rotTarget;
            switch (phase) {
                case ROTATE_180      -> enterSwapPhase(AnimPhase.SWAP_3, 8);
                case ROTATE_90       -> enterSwapPhase(AnimPhase.SWAP_4, 7);
                case ROTATE_90_FINAL -> enterSwapPhase(AnimPhase.SWAP_5, 5);
                default              -> {}
            }
        }
    }

    private void tickWaiting() {
        float pulse = 0.92f + 0.08f * (float) Math.sin(timer * 0.12f);
        for (SlotState s : slots) s.scale = pulse;
    }

    private void tickResultFlash() {
        float alpha = Math.max(0f, 1f - (timer % 19) / 18f);

        if (timer <= 18) {
            // First 18 ticks: flash clicked slot — green if correct, red if wrong
            if (clickedSlot >= 0) {
                if (resultIsCorrect) slots[clickedSlot].glowGreenAlpha = alpha;
                else                 slots[clickedSlot].flashRedAlpha  = alpha;
            }
        } else {
            // Clear first flash
            if (clickedSlot >= 0) {
                slots[clickedSlot].glowGreenAlpha = 0f;
                slots[clickedSlot].flashRedAlpha  = 0f;
            }
            // If wrong: reveal correct slot in green for ticks 18-36
            if (!resultIsCorrect) {
                slots[correctVisualSlot].glowGreenAlpha = Math.max(0f, 1f - (timer - 18) / 18f);
            }
        }

        if (timer >= 36) {
            for (SlotState s : slots) { s.glowGreenAlpha = 0f; s.flashRedAlpha = 0f; }
            enterPhase(AnimPhase.DONE);
        }
    }

    public void onSlotClicked(int slotIndex) {
        if (phase != AnimPhase.WAITING) return;
        clickedSlot     = slotIndex;
        resultIsCorrect = (slotIndex == correctVisualSlot);
        enterPhase(AnimPhase.RESULT_FLASH);
    }

    private void enterPhase(AnimPhase next)                            { phase = next; timer = 0; }
    private void enterSwapPhase(AnimPhase p, int count)                { enterPhase(p); swapsLeft = count; swapping = false; }
    private void enterRotationPhase(AnimPhase p, float deg, int ticks) { enterPhase(p); rotStart = groupRotation; rotTarget = groupRotation + deg; rotDuration = ticks; }

    private static float lerp(float a, float b, float t)  { return a + (b - a) * t; }
    private static float easeOutCubic(float t)             { float f = 1f - t; return 1f - f*f*f; }
    private static float easeInOutCubic(float t)           { return t < 0.5f ? 4*t*t*t : 1f-(float)Math.pow(-2*t+2,3)/2f; }
    private static float easeOutBack(float t)              { final float c1=1.70158f,c3=c1+1; return 1+c3*(float)Math.pow(t-1,3)+c1*(float)Math.pow(t-1,2); }
}