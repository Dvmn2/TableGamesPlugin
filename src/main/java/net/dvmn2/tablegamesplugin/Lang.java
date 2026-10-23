package net.dvmn2.tablegamesplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * RU/EN localisation of every player-visible text, written in MiniMessage.
 * <p>
 * Players receive feedback in the action bar ({@link #send}); the console receives a normal message.
 * {@link #sendChat} always uses chat and is meant for command feedback. Positive outcomes of
 * everyday actions are deliberately not announced. Placeholders are supplied only through
 * {@link TagResolver}s so player data can never inject MiniMessage tags.
 * <p>
 * The current language is the only static mutable state of the plugin. Thread: any.
 */
public final class Lang {

    /** Message identifiers; every key has an RU and an EN text. */
    public enum Key {
        ONLY_PLAYERS,
        GIVE_USAGE,
        NO_PLAYERS_FOUND,
        RELOADED,
        DECK_GIVEN,
        DECK_NAME_POKER,
        DECK_NAME_DURAK,
        INVALID_DECK_ITEM,
        DECK_ALREADY_ACTIVE,
        NO_CLEAR_SURFACE,
        DECK_EMPTY,
        WRONG_DECK,
        DECK_NOT_COLLECTED_IN_DISCARD,
        DECK_NOT_COLLECTED_IN_MAIN
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static volatile String language = "auto";

    static {
        put(Key.ONLY_PLAYERS,
                "<red>Эту команду может использовать только игрок.",
                "<red>This command can only be used by a player.");
        put(Key.GIVE_USAGE,
                "<red>Использование: /tablegames <usage>",
                "<red>Usage: /tablegames <usage>");
        put(Key.NO_PLAYERS_FOUND,
                "<red>Игроки не найдены.",
                "<red>No players found.");
        put(Key.RELOADED,
                "<green>Конфигурация перезагружена.",
                "<green>Configuration reloaded.");
        put(Key.DECK_GIVEN,
                "<green>Игроку <player> выдано: <deck>.",
                "<green>Gave <deck> to <player>.");
        put(Key.DECK_NAME_POKER,
                "Покерная колода",
                "Poker Deck");
        put(Key.DECK_NAME_DURAK,
                "Колода для дурака",
                "Durak Deck");
        put(Key.INVALID_DECK_ITEM,
                "<red>Этот предмет — некорректная или неподдерживаемая колода.",
                "<red>This item is an invalid or unsupported deck item.");
        put(Key.DECK_ALREADY_ACTIVE,
                "<red>Эта колода уже разложена на сервере.",
                "<red>This deck is already active on the server.");
        put(Key.NO_CLEAR_SURFACE,
                "<red>Здесь нет свободной ровной поверхности для колоды и стопки сброса.",
                "<red>This spot doesn't have a clear, solid surface for the deck and its discard pile.");
        put(Key.DECK_EMPTY,
                "<red>Колода пуста.",
                "<red>The deck is empty.");
        put(Key.WRONG_DECK,
                "<red>Эта карта не принадлежит этой колоде.",
                "<red>This card does not belong to this deck.");
        put(Key.DECK_NOT_COLLECTED_IN_DISCARD,
                "<red>Колода ещё не полностью собрана в стопке сброса.",
                "<red>The deck is not fully collected in the discard pile yet.");
        put(Key.DECK_NOT_COLLECTED_IN_MAIN,
                "<red>Не все карты лежат в колоде, собрать её нельзя.",
                "<red>Not all cards are in the deck, so it cannot be picked up.");
        verify();
    }

    private Lang() {
    }

    /** Sets {@code ru}, {@code en} or {@code auto}; anything else falls back to {@code auto}. */
    public static void setLanguage(String value) {
        language = value == null || value.isBlank() ? "auto" : value.toLowerCase(Locale.ROOT);
    }

    /** Renders {@code key} in the language that applies to {@code sender}. */
    public static Component get(Key key, CommandSender sender, TagResolver... resolvers) {
        return MM.deserialize(table(sender).get(key), resolvers);
    }

    /** Sends to the action bar of a player, or as a normal message to any other recipient. */
    public static void send(CommandSender recipient, Key key, TagResolver... resolvers) {
        Component message = get(key, recipient, resolvers);
        if (recipient instanceof Player player) {
            player.sendActionBar(message);
        } else {
            recipient.sendMessage(message);
        }
    }

    /** Sends to chat regardless of the recipient type. */
    public static void sendChat(CommandSender recipient, Key key, TagResolver... resolvers) {
        recipient.sendMessage(get(key, recipient, resolvers));
    }

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    private static void verify() {
        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    private static Map<Key, String> table(CommandSender sender) {
        return switch (language) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> sender instanceof Player player
                    && "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        };
    }
}
