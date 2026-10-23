package net.dvmn2.tablegamesplugin.util;

/**
 * Namespaced sound keys played by the plugin. They are registered by the accompanying resource pack
 * under the {@code table_games} namespace, so they are plain strings rather than {@link org.bukkit.Sound}.
 */
public final class PluginSounds {

    /** Collecting the fully assembled deck. */
    public static final String PICK_UP_FULL_DECK = "table_games:pick_up";

    /** Placing a deck item to create a physical deck. */
    public static final String PLACE_DECK = "table_games:place";

    /** Putting a card on the table or stacking it on another card. */
    public static final String PUT_CARD_ON_TABLE = "table_games:put";

    /** Shuffling a held deck item. */
    public static final String SHUFFLE_DECK = "table_games:shuffle";

    /** Taking a card off the table. */
    public static final String TAKE_CARD_FROM_TABLE = "table_games:get";

    /** Taking the top card of the main or discard pile. */
    public static final String TAKE_CARD_FROM_PILE = "table_games:take";

    /** Putting a held card into the main or discard pile. */
    public static final String PUT_CARD_INTO_PILE = "table_games:shove";

    private PluginSounds() {
    }
}
