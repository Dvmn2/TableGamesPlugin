package net.dvmn2.tablegamesplugin.storage.dto;

/**
 * Plain JSON-serializable representation of a {@link net.dvmn2.tablegamesplugin.model.Card}.
 * The owning deck's id is implicit from the enclosing {@link DeckDto}.
 */
public class CardDto {
    public String cardId;
    public String symbol;
}
