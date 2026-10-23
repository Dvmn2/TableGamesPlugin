package net.dvmn2.tablegamesplugin.model;

import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.util.CardSymbols;
import org.jetbrains.annotations.Nullable;

/**
 * The kinds of deck the plugin supports. The {@code table_games} marker value stored on the deck
 * item (see {@link #getMarkerValue()}) decides which one it is:
 * <ul>
 *     <li>{@code 0} {@link #POKER}: 52 cards (2..A), no trump</li>
 *     <li>{@code 1} {@link #DURAK}: 36 cards (6..A), the bottom card is the trump</li>
 * </ul>
 */
public enum DeckType {

    POKER(0, "poker", "Deck poker cards", Lang.Key.DECK_NAME_POKER,
            CardSymbols.FIRST_CODE_POINT, 52, false),

    // Durak skips ranks 2..5 (4 ranks of 4 suits), so it starts at the 6.
    DURAK(1, "durak", "Deck cards", Lang.Key.DECK_NAME_DURAK,
            CardSymbols.FIRST_CODE_POINT + 4 * CardSymbols.SUITS, 36, true);

    private final int markerValue;
    private final String commandName;
    private final String modelString;
    private final Lang.Key nameKey;
    private final int firstCodePoint;
    private final int deckSize;
    private final boolean hasTrump;

    DeckType(int markerValue, String commandName, String modelString, Lang.Key nameKey,
             int firstCodePoint, int deckSize, boolean hasTrump) {
        this.markerValue = markerValue;
        this.commandName = commandName;
        this.modelString = modelString;
        this.nameKey = nameKey;
        this.firstCodePoint = firstCodePoint;
        this.deckSize = deckSize;
        this.hasTrump = hasTrump;
    }

    /** Value stored under the {@code table_games} key of the deck item. */
    public int getMarkerValue() {
        return markerValue;
    }

    /** Literal used in {@code /tablegames give}. */
    public String getCommandName() {
        return commandName;
    }

    /** The custom-model-data string the resource pack maps to this deck's item model. */
    public String getModelString() {
        return modelString;
    }

    public Lang.Key getNameKey() {
        return nameKey;
    }

    public int getDeckSize() {
        return deckSize;
    }

    /** Whether the bottom card of the main pile is shown face up as a trump card. */
    public boolean hasTrump() {
        return hasTrump;
    }

    /** Whether {@code symbol} is one of this type's faces. */
    public boolean isValidSymbol(@Nullable String symbol) {
        return CardSymbols.isInRange(symbol, firstCodePoint, deckSize);
    }

    /** The deterministic order of a brand-new deck; index 0 is the bottom card. */
    public String[] initialOrder() {
        return CardSymbols.range(firstCodePoint, deckSize);
    }

    /** Returns the type with this marker value, or {@code null} for an unknown value. */
    @Nullable
    public static DeckType fromMarkerValue(int value) {
        for (DeckType type : values()) {
            if (type.markerValue == value) {
                return type;
            }
        }
        return null;
    }

    /** Returns the type with this name (case-insensitive), or {@code null} for an unknown name. */
    @Nullable
    public static DeckType fromName(@Nullable String name) {
        for (DeckType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
