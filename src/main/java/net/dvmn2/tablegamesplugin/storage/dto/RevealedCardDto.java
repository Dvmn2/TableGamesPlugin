package net.dvmn2.tablegamesplugin.storage.dto;

/** Plain representation of a card lying on the table. */
public final class RevealedCardDto {

    public String cardId;
    public String symbol;
    public String entityUuid;

    /** The click hitbox entity; absent for a card buried in a stack. */
    public String interactionEntityUuid;

    public String stackId;

    /** Whether only the card back is displayed. */
    public boolean faceDown;

    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
}
