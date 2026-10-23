package net.dvmn2.tablegamesplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The complete logical state of one physical, currently active playing card deck.
 * <p>
 * Within the main and discard piles index 0 is the BOTTOM card and the last index is the TOP card.
 * The piles and the table are exposed as read-only views; all changes go through the methods of
 * this class. Thread: main server thread.
 */
public final class ActiveDeck {

    private final UUID deckId;
    private final DeckType type;
    private final List<Card> definition;
    private final Anchor anchor;
    private final List<Card> mainPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final List<RevealedCardInstance> revealedCards = new ArrayList<>();

    @Nullable
    private UUID mainDisplayId;
    @Nullable
    private UUID discardDisplayId;
    @Nullable
    private UUID trumpDisplayId;
    @Nullable
    private UUID mainInteractionId;
    @Nullable
    private UUID discardInteractionId;

    /**
     * @param definition the fixed set of every card that belongs to this deck
     */
    public ActiveDeck(UUID deckId, DeckType type, List<Card> definition, Anchor anchor) {
        this.deckId = deckId;
        this.type = type;
        this.definition = List.copyOf(definition);
        this.anchor = anchor;
    }

    public UUID getDeckId() {
        return deckId;
    }

    public DeckType getType() {
        return type;
    }

    public List<Card> getDefinition() {
        return definition;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    // ---------- Piles ----------

    public List<Card> getMainPile() {
        return Collections.unmodifiableList(mainPile);
    }

    public List<Card> getDiscardPile() {
        return Collections.unmodifiableList(discardPile);
    }

    public void pushMain(Card card) {
        mainPile.add(card);
    }

    /** Inserts at {@code index}, shifting the cards above it up. */
    public void insertMain(int index, Card card) {
        mainPile.add(index, card);
    }

    /** Removes and returns the top card, or {@code null} when the pile is empty. */
    @Nullable
    public Card popMain() {
        return mainPile.isEmpty() ? null : mainPile.remove(mainPile.size() - 1);
    }

    public void pushDiscard(Card card) {
        discardPile.add(card);
    }

    /** Inserts at {@code index}, shifting the cards above it up. */
    public void insertDiscard(int index, Card card) {
        discardPile.add(index, card);
    }

    /** Removes and returns the top card, or {@code null} when the pile is empty. */
    @Nullable
    public Card popDiscard() {
        return discardPile.isEmpty() ? null : discardPile.remove(discardPile.size() - 1);
    }

    /** The bottom card of the main pile, or {@code null} for a deck without trump or an empty pile. */
    @Nullable
    public Card getTrumpCard() {
        if (!type.hasTrump() || mainPile.isEmpty()) {
            return null;
        }
        return mainPile.get(0);
    }

    // ---------- Table ----------

    public List<RevealedCardInstance> getRevealedCards() {
        return Collections.unmodifiableList(revealedCards);
    }

    public void addRevealed(RevealedCardInstance instance) {
        revealedCards.add(instance);
    }

    public void removeRevealed(RevealedCardInstance instance) {
        revealedCards.remove(instance);
    }

    /** Finds the table card with this id, or {@code null}. */
    @Nullable
    public RevealedCardInstance findRevealed(UUID cardId) {
        for (RevealedCardInstance candidate : revealedCards) {
            if (candidate.getCard().cardId().equals(cardId)) {
                return candidate;
            }
        }
        return null;
    }

    // ---------- Entities ----------

    @Nullable
    public UUID getMainDisplayId() {
        return mainDisplayId;
    }

    public void setMainDisplayId(@Nullable UUID mainDisplayId) {
        this.mainDisplayId = mainDisplayId;
    }

    @Nullable
    public UUID getDiscardDisplayId() {
        return discardDisplayId;
    }

    public void setDiscardDisplayId(@Nullable UUID discardDisplayId) {
        this.discardDisplayId = discardDisplayId;
    }

    @Nullable
    public UUID getTrumpDisplayId() {
        return trumpDisplayId;
    }

    public void setTrumpDisplayId(@Nullable UUID trumpDisplayId) {
        this.trumpDisplayId = trumpDisplayId;
    }

    /** The invisible click hitbox of the main pile; {@code null} once the pile was dealt out. */
    @Nullable
    public UUID getMainInteractionId() {
        return mainInteractionId;
    }

    public void setMainInteractionId(@Nullable UUID mainInteractionId) {
        this.mainInteractionId = mainInteractionId;
    }

    @Nullable
    public UUID getDiscardInteractionId() {
        return discardInteractionId;
    }

    public void setDiscardInteractionId(@Nullable UUID discardInteractionId) {
        this.discardInteractionId = discardInteractionId;
    }

    /** Whether {@code entityId} is one of the entities this deck currently owns. */
    public boolean referencesEntity(UUID entityId) {
        if (entityId.equals(mainDisplayId) || entityId.equals(discardDisplayId) || entityId.equals(trumpDisplayId)
                || entityId.equals(mainInteractionId) || entityId.equals(discardInteractionId)) {
            return true;
        }
        for (RevealedCardInstance revealed : revealedCards) {
            if (entityId.equals(revealed.getEntityUuid()) || entityId.equals(revealed.getInteractionEntityUuid())) {
                return true;
            }
        }
        return false;
    }

    // ---------- Collection ----------

    /**
     * Whether the deck can be reassembled from the discard pile: the main pile and the table are
     * empty and the discard pile holds exactly the cards that belong to this deck.
     */
    public boolean isFullyCollectedInDiscard() {
        if (!mainPile.isEmpty() || !revealedCards.isEmpty()) {
            return false;
        }
        return containsExactlyDefinition(discardPile);
    }

    /**
     * Whether the deck can be picked up straight from the main pile: the discard pile and the table
     * are empty and the main pile holds exactly the cards that belong to this deck. Cards held by
     * players are in no pile, so they make this fail.
     */
    public boolean isFullyCollectedInMain() {
        if (!discardPile.isEmpty() || !revealedCards.isEmpty()) {
            return false;
        }
        return containsExactlyDefinition(mainPile);
    }

    private boolean containsExactlyDefinition(List<Card> pile) {
        if (pile.size() != definition.size()) {
            return false;
        }
        Set<UUID> pileIds = new HashSet<>();
        for (Card card : pile) {
            pileIds.add(card.cardId());
        }
        if (pileIds.size() != definition.size()) {
            return false;
        }
        for (Card card : definition) {
            if (!pileIds.contains(card.cardId())) {
                return false;
            }
        }
        return true;
    }
}
