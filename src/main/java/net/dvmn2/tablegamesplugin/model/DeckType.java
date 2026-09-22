package net.dvmn2.tablegamesplugin.model;

import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.util.CardSymbols;

/**
 * The kinds of deck the plugin supports. The deck item's {@code playing_cards} value
 * (see {@link #getMarkerValue()}) decides which one it is:
 * <ul>
 *     <li>{@code playing_cards: 0} - {@link #POKER}: 52 cards (2..A), no trump</li>
 *     <li>{@code playing_cards: 1} - {@link #DURAK}: 36 cards (6..A), bottom card is the trump</li>
 * </ul>
 */
public enum DeckType {

    POKER(0, "poker", "Deck poker cards", Lang.Key.DECK_NAME_POKER,
            CardSymbols.FIRST_CODE_POINT, 52, false),

    // Durak skips ranks 2..5 (4 ranks x 4 suits), i.e. starts at the 6.
    DURAK(1, "durak", "Deck cards", Lang.Key.DECK_NAME_DURAK,
            CardSymbols.FIRST_CODE_POINT + 4 * CardSymbols.SUITS, 36, true);

    private final int markerValue;
    private final String commandName;
    private final String component;
    private final Lang.Key nameKey;
    private final int firstCodePoint;
    private final int deckSize;
    private final boolean hasTrump;

    DeckType(int markerValue, String commandName, String component, Lang.Key nameKey,
             int firstCodePoint, int deckSize, boolean hasTrump) {
        this.markerValue = markerValue;
        this.commandName = commandName;
        this.component = component;
        this.nameKey = nameKey;
        this.firstCodePoint = firstCodePoint;
        this.deckSize = deckSize;
        this.hasTrump = hasTrump;
    }

    /**
     * Value stored under the {@code playing_cards} key of the deck item.
     */
    public int getMarkerValue() {
        return markerValue;
    }

    /**
     * Literal used in {@code /tablegames give <name>}.
     */
    public String getCommandName() {
        return commandName;
    }

    public String getComponent() {
        return component;
    }

    public Lang.Key getNameKey() {
        return nameKey;
    }

    public int getDeckSize() {
        return deckSize;
    }

    /**
     * Whether the bottom card of the main pile is shown face-up as a trump card.
     */
    public boolean hasTrump() {
        return hasTrump;
    }

    /**
     * Whether {@code symbol} is one of this deck type's {@link #getDeckSize()} faces.
     */
    public boolean isValidSymbol(String symbol) {
        return CardSymbols.isInRange(symbol, firstCodePoint, deckSize);
    }

    /**
     * Deterministic initial order of a brand-new deck; index 0 is the bottom card.
     */
    public String[] initialOrder() {
        return CardSymbols.range(firstCodePoint, deckSize);
    }

    /**
     * @return the type whose {@link #getMarkerValue()} equals {@code value}, or {@code null}
     * if the value is unknown (such a deck item is treated as invalid).
     */
    public static DeckType fromMarkerValue(int value) {
        for (DeckType type : values()) {
            if (type.markerValue == value) {
                return type;
            }
        }
        return null;
    }
}