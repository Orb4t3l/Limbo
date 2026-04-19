package com.orbital.limbomod.renderer;

/**
 * Thin client-only singleton that the renderer writes to each frame.
 *
 * The renderer calculates which LimboDisplayEntity slot the player is currently
 * aiming at by ray-casting against each slot's world position.
 * The ClientClickHandler reads these values when a right-click fires to know
 * which entity + slot to send a SelectItemPacket for.
 */
public final class ClientHoverState {

    private ClientHoverState() {}

    /** Entity ID of the LimboDisplayEntity currently being hovered, or -1. */
    public static int hoveredEntityId  = -1;

    /** Visual slot index (0-7) within that entity being hovered, or -1. */
    public static int hoveredSlotIndex = -1;

    /** Clears hover state (called when the player is not looking at any display). */
    public static void clear() {
        hoveredEntityId  = -1;
        hoveredSlotIndex = -1;
    }
}