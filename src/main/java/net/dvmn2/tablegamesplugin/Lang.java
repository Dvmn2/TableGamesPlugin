package net.dvmn2.tablegamesplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * RU/EN локализация плагина. Язык задаётся в config.yml (settings.language):
 * "ru" / "en" — фиксированный язык; "auto" (по умолчанию) — берётся из
 * клиентской локали игрока (Player#locale()), для не-игроков — английский.
 * <p>
 * Сообщения игроку отправляются через {@link #send(CommandSender, Key, Object...)} в action bar
 * (не в чат); положительные («успешные») уведомления не выводятся вовсе.
 * Текст для чата (например, справка по команде) — {@link #sendChat(CommandSender, Key, Object...)}.
 * Шаблоны используют legacy-коды цвета ({@code §c}) и {@code %s}-подстановки.
 */
public final class Lang {

    public enum Key {
        // /tablegames
        NOT_A_PLAYER,
        GIVE_USAGE,

        // Названия предметов-колод (подставляются в display name)
        DECK_NAME_POKER,
        DECK_NAME_DURAK,

        // Взаимодействие с колодой
        INVALID_DECK_ITEM,
        DECK_ALREADY_ACTIVE,
        NO_CLEAR_SURFACE,
        DECK_EMPTY,
        WRONG_DECK,
        DECK_NOT_COLLECTED_IN_DISCARD,
        DECK_NOT_COLLECTED_IN_MAIN
    }

    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    static {
        RU.put(Key.NOT_A_PLAYER, "§cЭту команду может использовать только игрок.");
        RU.put(Key.GIVE_USAGE, "§cИспользование: /tablegames give <%s>");
        RU.put(Key.DECK_NAME_POKER, "Покерная колода");
        RU.put(Key.DECK_NAME_DURAK, "Колода для дурака");
        RU.put(Key.INVALID_DECK_ITEM, "§cЭтот предмет — некорректная или неподдерживаемая колода.");
        RU.put(Key.DECK_ALREADY_ACTIVE, "§cЭта колода уже разложена на сервере.");
        RU.put(Key.NO_CLEAR_SURFACE, "§cЗдесь нет свободной ровной поверхности для колоды и стопки сброса.");
        RU.put(Key.DECK_EMPTY, "§cКолода пуста.");
        RU.put(Key.WRONG_DECK, "§cЭта карта не принадлежит этой колоде.");
        RU.put(Key.DECK_NOT_COLLECTED_IN_DISCARD, "§cКолода ещё не полностью собрана в стопке сброса.");
        RU.put(Key.DECK_NOT_COLLECTED_IN_MAIN, "§cНе все карты лежат в колоде, собрать её нельзя.");

        EN.put(Key.NOT_A_PLAYER, "§cThis command can only be used by a player.");
        EN.put(Key.GIVE_USAGE, "§cUsage: /tablegames give <%s>");
        EN.put(Key.DECK_NAME_POKER, "Poker Deck");
        EN.put(Key.DECK_NAME_DURAK, "Durak Deck");
        EN.put(Key.INVALID_DECK_ITEM, "§cThis item is an invalid or unsupported deck item.");
        EN.put(Key.DECK_ALREADY_ACTIVE, "§cThis deck is already active on the server.");
        EN.put(Key.NO_CLEAR_SURFACE, "§cThis spot doesn't have a clear, solid surface for the deck and its discard pile.");
        EN.put(Key.DECK_EMPTY, "§cThe deck is empty.");
        EN.put(Key.WRONG_DECK, "§cThis card does not belong to this deck.");
        EN.put(Key.DECK_NOT_COLLECTED_IN_DISCARD, "§cThe deck is not fully collected in the discard pile yet.");
        EN.put(Key.DECK_NOT_COLLECTED_IN_MAIN, "§cNot all cards are in the deck, so it cannot be picked up.");

        // Fail fast if a key was added to the enum but not translated.
        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    private static volatile String configuredLanguage = "auto";

    private Lang() {
    }

    public static void setLanguage(String language) {
        configuredLanguage = (language == null || language.isBlank())
                ? "auto"
                : language.toLowerCase(Locale.ROOT);
    }

    public static String get(Key key, CommandSender sender, Object... args) {
        Map<Key, String> table = resolveTable(sender);
        String template = table.getOrDefault(key, EN.get(key));
        return args.length == 0 ? template : String.format(Locale.US, template, args);
    }

    /**
     * Same as {@link #get}, converted from legacy {@code §} codes to an Adventure component.
     */
    public static Component component(Key key, CommandSender sender, Object... args) {
        return LegacyComponentSerializer.legacySection().deserialize(get(key, sender, args));
    }

    /**
     * Sends a localized message to {@code recipient}, in the recipient's language.
     * Players receive it in the action bar; other senders (console) receive it as a regular message.
     */
    public static void send(CommandSender recipient, Key key, Object... args) {
        Component message = component(key, recipient, args);
        if (recipient instanceof Player player) {
            player.sendActionBar(message);
        } else {
            recipient.sendMessage(message);
        }
    }

    /**
     * Sends a localized message to the chat regardless of the recipient type.
     */
    public static void sendChat(CommandSender recipient, Key key, Object... args) {
        recipient.sendMessage(component(key, recipient, args));
    }

    private static Map<Key, String> resolveTable(CommandSender sender) {
        return switch (configuredLanguage) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> autoResolve(sender);
        };
    }

    private static Map<Key, String> autoResolve(CommandSender sender) {
        if (sender instanceof Player player) {
            return "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        }
        return EN;
    }
}