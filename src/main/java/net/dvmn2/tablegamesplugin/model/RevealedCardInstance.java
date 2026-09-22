package net.dvmn2.tablegamesplugin.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * A card that has been put on the table, tracked together with the TextDisplay
 * entity representing it, the Interaction entity that makes it clickable (TextDisplay
 * has no interaction hitbox of its own — see
 * {@link net.dvmn2.tablegamesplugin.display.DisplayFactory}), and its exact world
 * position/yaw.
 * <p>
 * A card is either face-up (its symbol is shown) or face-down (only the card back is
 * shown; the real identity is kept here, server-side). Cards that were stacked onto each
 * other share a {@link #getStackId() stack id}: stacks can no longer be recognised by
 * identical X/Z, because face-down stacks are deliberately offset card by card.
 */
public final class RevealedCardInstance {

    private final Card card;
    private final UUID stackId;
    private UUID entityUuid;
    private UUID interactionEntityUuid;
    private Location location;
    private float yaw;
    private boolean faceDown;

    public RevealedCardInstance(Card card, UUID entityUuid, UUID interactionEntityUuid,
                                Location location, float yaw, UUID stackId, boolean faceDown) {
        this.card = card;
        this.entityUuid = entityUuid;
        this.interactionEntityUuid = interactionEntityUuid;
        this.location = location.clone();
        this.yaw = yaw;
        this.stackId = stackId;
        this.faceDown = faceDown;
    }

    public Card getCard() {
        return card;
    }

    /**
     * Identifies the stack this card belongs to (a lone card is a stack of one).
     */
    public UUID getStackId() {
        return stackId;
    }

    public boolean isFaceDown() {
        return faceDown;
    }

    public void setFaceDown(boolean faceDown) {
        this.faceDown = faceDown;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public UUID getInteractionEntityUuid() {
        return interactionEntityUuid;
    }

    public void setInteractionEntityUuid(UUID interactionEntityUuid) {
        this.interactionEntityUuid = interactionEntityUuid;
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
}