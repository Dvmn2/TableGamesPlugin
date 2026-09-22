package net.dvmn2.tablegamesplugin.storage.dto;

public class RevealedCardDto {
    public String cardId;
    public String symbol;
    public String entityUuid;

    /**
     * The Interaction entity that is the actual clickable hitbox for this card.
     */
    public String interactionEntityUuid;

    /**
     * Identifies the stack this card belongs to. Missing in files written before stacks
     * had ids; those cards are grouped by identical X/Z on load.
     */
    public String stackId;

    /**
     * Whether the card lies face-down (only its back is displayed). Absent = false.
     */
    public boolean faceDown;

    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
}