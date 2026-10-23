package net.dvmn2.tablegamesplugin.storage.dto;

/** Plain representation of a card; the owning deck's id is implicit from the enclosing {@link DeckDto}. */
public final class CardDto {

    public String cardId;
    public String symbol;
}
