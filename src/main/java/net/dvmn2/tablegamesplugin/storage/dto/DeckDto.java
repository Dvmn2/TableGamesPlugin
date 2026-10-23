package net.dvmn2.tablegamesplugin.storage.dto;

import java.util.List;

/** Plain representation of one active deck. */
public final class DeckDto {

    public String deckId;

    /** {@link net.dvmn2.tablegamesplugin.model.DeckType} constant name. */
    public String deckType;

    /** The fixed set of all cards belonging to this deck. */
    public List<CardDto> definition;

    /** Main pile, bottom (index 0) to top (last). */
    public List<CardDto> mainPile;

    /** Discard pile, bottom (index 0) to top (last). */
    public List<CardDto> discardPile;

    public List<RevealedCardDto> revealedCards;

    public String world;
    public int anchorBlockX;
    public int anchorBlockY;
    public int anchorBlockZ;

    public String mainDisplayEntityId;
    public String discardDisplayEntityId;
    public String trumpDisplayEntityId;
    public String mainInteractionEntityId;
    public String discardInteractionEntityId;
}
