package net.dvmn2.tablegamesplugin.model;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A card lying on the table, tracked together with the text display that shows it, the
 * {@code Interaction} entity that makes it clickable, and its exact position.
 * <p>
 * A card is face up (its symbol is shown) or face down (only the back is shown; the real identity
 * stays server-side). Cards stacked on each other share a stack id, because face-down stacks are
 * offset card by card and cannot be recognised by position. Only the top card of a stack carries a
 * click hitbox. Thread: main server thread.
 */
public final class RevealedCardInstance {

    private final Card card;
    private final UUID stackId;
    private final UUID entityUuid;
    private final Location location;
    private final float yaw;
    @Nullable
    private UUID interactionEntityUuid;
    private boolean faceDown;

    public RevealedCardInstance(Card card, UUID entityUuid, @Nullable UUID interactionEntityUuid,
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

    /** Identifies the stack this card belongs to; a lone card is a stack of one. */
    public UUID getStackId() {
        return stackId;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    /** The click hitbox; {@code null} for a card that is buried in a stack. */
    @Nullable
    public UUID getInteractionEntityUuid() {
        return interactionEntityUuid;
    }

    public void setInteractionEntityUuid(@Nullable UUID interactionEntityUuid) {
        this.interactionEntityUuid = interactionEntityUuid;
    }

    /** A copy of the position; its world may be {@code null} if the world was not loaded at startup. */
    public Location getLocation() {
        return location.clone();
    }

    public float getYaw() {
        return yaw;
    }

    public boolean isFaceDown() {
        return faceDown;
    }

    public void setFaceDown(boolean faceDown) {
        this.faceDown = faceDown;
    }
}
