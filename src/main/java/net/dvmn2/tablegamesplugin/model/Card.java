package net.dvmn2.tablegamesplugin.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A single physical playing card: an immutable identity (cardId), the deck it is
 * permanently bound to (deckId), and its face symbol.
 */
public final class Card {

    private final UUID cardId;
    private final UUID deckId;
    private final String symbol;

    public Card(UUID cardId, UUID deckId, String symbol) {
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.deckId = Objects.requireNonNull(deckId, "deckId");
        this.symbol = Objects.requireNonNull(symbol, "symbol");
    }

    public UUID getCardId() {
        return cardId;
    }

    public UUID getDeckId() {
        return deckId;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Card card)) {
            return false;
        }
        return cardId.equals(card.cardId);
    }

    @Override
    public int hashCode() {
        return cardId.hashCode();
    }

    @Override
    public String toString() {
        return "Card{cardId=" + cardId + ", deckId=" + deckId + ", symbol='" + symbol + "'}";
    }
}
