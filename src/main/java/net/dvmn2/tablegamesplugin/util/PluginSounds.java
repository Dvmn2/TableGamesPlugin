package net.dvmn2.tablegamesplugin.util;

/**
 * Namespaced sound keys played by this plugin (registered by an accompanying resource
 * pack under the {@code table_games} namespace). Kept as plain strings, not the vanilla
 * {@link org.bukkit.Sound} enum, since these are custom, non-vanilla sound events.
 */
public final class PluginSounds {

    private PluginSounds() {
    }

    /**
     * Collecting the fully-assembled deck out of the discard pile.
     */
    public static final String PICK_UP_FULL_DECK = "table_games:pick_up";

    /**
     * Placing a full deck item on a block to create a physical deck.
     */
    public static final String PLACE_DECK = "table_games:plase";

    /**
     * Revealing a new card on the table, or stacking one on an already-revealed card.
     */
    public static final String PUT_CARD_ON_TABLE = "table_games:put";

    /**
     * Shuffling a held deck item.
     */
    public static final String SHUFFLE_DECK = "table_games:shafle";

    /**
     * Taking a revealed card off the table.
     */
    public static final String TAKE_CARD_FROM_TABLE = "table_games:get";

    /**
     * Taking the top card from the main pile or from the discard pile.
     */
    public static final String TAKE_CARD_FROM_PILE = "table_games:take";

    /**
     * Placing a held card into the main pile or into the discard pile.
     */
    public static final String PUT_CARD_INTO_PILE = "table_games:shove";
}