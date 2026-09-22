package net.dvmn2.tablegamesplugin.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

/**
 * The complete logical state of one physical, currently-active playing card deck.
 * <p>
 * Ordering convention (see specification section 5): within {@link #getMainPile()} and
 * {@link #getDiscardPile()}, index 0 is the BOTTOM card and the LAST index is the TOP
 * card.
 */
public final class ActiveDeck {

    private final UUID deckId;
    private final DeckType type;

    /**
     * The fixed set of all cards that belong to this deck ({@link DeckType#getDeckSize()}
     * of them: 52 for poker, 36 for durak), defined at creation time.
     */
    private final List<Card> definition;

    private final List<Card> mainPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final List<RevealedCardInstance> revealedCards = new ArrayList<>();

    private String worldName;
    private int anchorBlockX;
    private int anchorBlockY;
    private int anchorBlockZ;

    private UUID mainDisplayEntityId;
    private UUID discardDisplayEntityId;
    private UUID trumpDisplayEntityId;

    /**
     * The invisible {@code Interaction} entities that actually receive clicks for the
     * main pile and discard pile respectively; {@code Display} entities have no
     * interaction hitbox of their own (see {@link net.dvmn2.tablegamesplugin.display.DisplayFactory}).
     */
    private UUID mainInteractionEntityId;
    private UUID discardInteractionEntityId;

    private float mainDisplayYaw;
    private float discardDisplayYaw;

    public ActiveDeck(UUID deckId, DeckType type, List<Card> definition) {
        this.deckId = deckId;
        this.type = type;
        this.definition = new ArrayList<>(definition);
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

    public List<Card> getMainPile() {
        return mainPile;
    }

    public List<Card> getDiscardPile() {
        return discardPile;
    }

    public List<RevealedCardInstance> getRevealedCards() {
        return revealedCards;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public int getAnchorBlockX() {
        return anchorBlockX;
    }

    public void setAnchorBlockX(int anchorBlockX) {
        this.anchorBlockX = anchorBlockX;
    }

    public int getAnchorBlockY() {
        return anchorBlockY;
    }

    public void setAnchorBlockY(int anchorBlockY) {
        this.anchorBlockY = anchorBlockY;
    }

    public int getAnchorBlockZ() {
        return anchorBlockZ;
    }

    public void setAnchorBlockZ(int anchorBlockZ) {
        this.anchorBlockZ = anchorBlockZ;
    }

    public UUID getMainDisplayEntityId() {
        return mainDisplayEntityId;
    }

    public void setMainDisplayEntityId(UUID mainDisplayEntityId) {
        this.mainDisplayEntityId = mainDisplayEntityId;
    }

    public UUID getDiscardDisplayEntityId() {
        return discardDisplayEntityId;
    }

    public void setDiscardDisplayEntityId(UUID discardDisplayEntityId) {
        this.discardDisplayEntityId = discardDisplayEntityId;
    }

    public UUID getTrumpDisplayEntityId() {
        return trumpDisplayEntityId;
    }

    public void setTrumpDisplayEntityId(UUID trumpDisplayEntityId) {
        this.trumpDisplayEntityId = trumpDisplayEntityId;
    }

    public UUID getMainInteractionEntityId() {
        return mainInteractionEntityId;
    }

    public void setMainInteractionEntityId(UUID mainInteractionEntityId) {
        this.mainInteractionEntityId = mainInteractionEntityId;
    }

    public UUID getDiscardInteractionEntityId() {
        return discardInteractionEntityId;
    }

    public void setDiscardInteractionEntityId(UUID discardInteractionEntityId) {
        this.discardInteractionEntityId = discardInteractionEntityId;
    }

    public float getMainDisplayYaw() {
        return mainDisplayYaw;
    }

    public void setMainDisplayYaw(float mainDisplayYaw) {
        this.mainDisplayYaw = mainDisplayYaw;
    }

    public float getDiscardDisplayYaw() {
        return discardDisplayYaw;
    }

    public void setDiscardDisplayYaw(float discardDisplayYaw) {
        this.discardDisplayYaw = discardDisplayYaw;
    }

    /**
     * The bottom-most card of the main pile, i.e. the trump card. {@code null} if the pile
     * is empty or this deck type has no trump (see {@link DeckType#hasTrump()}).
     */
    public Card getTrumpCard() {
        if (!type.hasTrump() || mainPile.isEmpty()) {
            return null;
        }
        return mainPile.get(0);
    }

    public Location getAnchorTopSurfaceCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, anchorBlockX + 0.5, anchorBlockY + 1.0, anchorBlockZ + 0.5);
    }

    public Location getDiscardTopSurfaceCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, anchorBlockX + 1 + 0.5, anchorBlockY + 1.0, anchorBlockZ + 0.5);
    }

    /**
     * A deck may be reassembled from the discard pile only if the main pile and the
     * table are both empty, and the discard pile contains exactly the unique cards
     * that belong to this deck (see specification sections 22-23).
     */
    public boolean isFullyCollectedInDiscard() {
        if (!mainPile.isEmpty() || !revealedCards.isEmpty()) {
            return false;
        }
        return containsExactlyDefinition(discardPile);
    }

    /**
     * A deck may be picked up straight from the main (dealing) pile only if every card of
     * it is still there: the discard pile and the table are both empty, and the main pile
     * contains exactly the unique cards that belong to this deck. (Cards held by players
     * are in no pile, so they make the check fail, as they should.)
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
            pileIds.add(card.getCardId());
        }
        if (pileIds.size() != definition.size()) {
            return false;
        }
        for (Card card : definition) {
            if (!pileIds.contains(card.getCardId())) {
                return false;
            }
        }
        return true;
    }
}