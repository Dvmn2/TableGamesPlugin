package net.dvmn2.tablegamesplugin.util;

import net.dvmn2.tablegamesplugin.TableGamesPlugin;
import org.bukkit.NamespacedKey;

/**
 * Single source of truth for every {@link org.bukkit.persistence.PersistentDataContainer}
 * key used by the plugin, plus the string values stored under the "type" key.
 */
public final class PluginKeys {

    public static final String TYPE_DECK = "deck";
    public static final String TYPE_DISCARD = "discard";
    public static final String TYPE_TRUMP_DISPLAY = "trump_display";
    public static final String TYPE_REVEALED_CARD = "revealed_card";

    private PluginKeys() {
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(TableGamesPlugin.getInstance(), name);
    }

    public static NamespacedKey type() {
        return key("type");
    }

    public static NamespacedKey deckId() {
        return key("deck_id");
    }

    public static NamespacedKey cardId() {
        return key("card_id");
    }

    public static NamespacedKey symbol() {
        return key("symbol");
    }

    public static NamespacedKey tableGames() {
        return key("table_games");
    }

    public static NamespacedKey cards() {
        return key("cards");
    }
}
