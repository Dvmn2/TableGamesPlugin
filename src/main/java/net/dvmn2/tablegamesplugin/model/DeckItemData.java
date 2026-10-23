package net.dvmn2.tablegamesplugin.model;

import java.util.List;
import java.util.UUID;

/**
 * The validated content of a full deck item.
 *
 * @param deckId the deck identity
 * @param type   the deck type
 * @param cards  all cards of the deck ordered bottom (index 0) to top (last index)
 */
public record DeckItemData(UUID deckId, DeckType type, List<Card> cards) {

    public DeckItemData {
        cards = List.copyOf(cards);
    }
}
