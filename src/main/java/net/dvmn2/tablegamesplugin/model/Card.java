package net.dvmn2.tablegamesplugin.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A single physical playing card.
 *
 * @param cardId unique identity of the card
 * @param deckId the deck the card is permanently bound to
 * @param symbol the face glyph, see {@link net.dvmn2.tablegamesplugin.util.CardSymbols}
 */
public record Card(UUID cardId, UUID deckId, String symbol) {

    public Card {
        Objects.requireNonNull(cardId, "cardId");
        Objects.requireNonNull(deckId, "deckId");
        Objects.requireNonNull(symbol, "symbol");
    }
}
