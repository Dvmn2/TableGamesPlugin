package net.dvmn2.tablegamesplugin.storage.dto;

import java.util.List;

public class DeckDto {
    public String deckId;

    /**
     * {@link net.dvmn2.tablegamesplugin.model.DeckType} constant name ("POKER"/"DURAK").
     * Missing in files written before deck types existed; such decks are treated as DURAK
     * (the only, 36-card deck back then).
     */
    public String deckType;

    /**
     * The fixed set of all cards belonging to this deck (52 for poker, 36 for durak).
     */
    public List<CardDto> definition;

    /**
     * Current order of the main pile, bottom (index 0) to top (last).
     */
    public List<CardDto> mainPile;

    /**
     * Current order of the discard pile, bottom (index 0) to top (last).
     */
    public List<CardDto> discardPile;

    public List<RevealedCardDto> revealedCards;

    public String world;
    public int anchorBlockX;
    public int anchorBlockY;
    public int anchorBlockZ;

    public String mainDisplayEntityId;
    public String discardDisplayEntityId;
    public String trumpDisplayEntityId;

    /**
     * Interaction entities (the actual clickable hitboxes) for the main and discard piles.
     */
    public String mainInteractionEntityId;
    public String discardInteractionEntityId;

    public float mainDisplayYaw;
    public float discardDisplayYaw;
}