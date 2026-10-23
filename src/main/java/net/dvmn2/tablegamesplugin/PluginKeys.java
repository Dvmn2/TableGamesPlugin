package net.dvmn2.tablegamesplugin;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Single source of every {@link NamespacedKey} the plugin stores in item and entity data containers. */
public final class PluginKeys {

    private final NamespacedKey type;
    private final NamespacedKey deckId;
    private final NamespacedKey cardId;
    private final NamespacedKey symbol;
    private final NamespacedKey tableGames;
    private final NamespacedKey cards;

    public PluginKeys(Plugin plugin) {
        this.type = new NamespacedKey(plugin, "type");
        this.deckId = new NamespacedKey(plugin, "deck_id");
        this.cardId = new NamespacedKey(plugin, "card_id");
        this.symbol = new NamespacedKey(plugin, "symbol");
        this.tableGames = new NamespacedKey(plugin, "table_games");
        this.cards = new NamespacedKey(plugin, "cards");
    }

    /** Entity role, see {@link net.dvmn2.tablegamesplugin.model.EntityRole}. */
    public NamespacedKey type() {
        return type;
    }

    public NamespacedKey deckId() {
        return deckId;
    }

    public NamespacedKey cardId() {
        return cardId;
    }

    public NamespacedKey symbol() {
        return symbol;
    }

    /** Deck item marker holding the deck type's marker value. */
    public NamespacedKey tableGames() {
        return tableGames;
    }

    /** Ordered card list stored on a deck item. */
    public NamespacedKey cards() {
        return cards;
    }
}
