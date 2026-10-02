package net.dvmn2.tablegamesplugin.manager;

import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.DeckType;
import net.dvmn2.tablegamesplugin.util.PluginKeys;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Builds and parses the deck item's {@code custom_data}:
 * <pre>
 * custom_data: {
 *   playing_cards: 0 | 1,          // 0 = poker (52 cards), 1 = durak (36 cards)
 *   deck_id: UUID,
 *   cards: [ { card_id: UUID, symbol: String }, ... ]   // bottom -&gt; top
 * }
 * </pre>
 * A marker-only item (only {@code playing_cards}) is deliberately treated as an
 * invalid/unsupported deck item, per specification section 4.
 */
public final class DeckItemFactory {

    private DeckItemFactory() {
    }

    /**
     * @param viewer whose language the item's display name is written in (the name is
     *               baked into the item, so it does not change if another player holds it)
     */
    public static ItemStack createFullDeckItem(UUID deckId, DeckType type, List<Card> orderedCards, CommandSender viewer) {
        ItemStack item = new ItemStack(Material.COPPER_INGOT);
        ItemMeta meta = item.getItemMeta();

        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setStrings(List.of(type.getComponent()));
        meta.setCustomModelDataComponent(component);

        meta.displayName(Lang.component(type.getNameKey(), viewer));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(PluginKeys.tableGames(), PersistentDataType.INTEGER, type.getMarkerValue());
        pdc.set(PluginKeys.deckId(), PersistentDataType.STRING, deckId.toString());

        PersistentDataContainer[] cardContainers = new PersistentDataContainer[orderedCards.size()];
        for (int i = 0; i < orderedCards.size(); i++) {
            Card card = orderedCards.get(i);
            PersistentDataContainer cardPdc = pdc.getAdapterContext().newPersistentDataContainer();
            cardPdc.set(PluginKeys.cardId(), PersistentDataType.STRING, card.getCardId().toString());
            cardPdc.set(PluginKeys.symbol(), PersistentDataType.STRING, card.getSymbol());
            cardContainers[i] = cardPdc;
        }
        pdc.set(PluginKeys.cards(), PersistentDataType.LIST.dataContainers(), Arrays.asList(cardContainers));

        item.setItemMeta(meta);
        return item;
    }

    public static boolean hasTableGamesMarker(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(PluginKeys.tableGames(), PersistentDataType.INTEGER);
    }

    /**
     * @return the fully parsed deck data if this item is a complete, valid deck item
     * (a known {@code table_games} type, a {@code deck_id}, and exactly as many
     * well-formed cards as that type has, with unique ids AND unique symbols, all of them
     * valid for that type), otherwise {@code null} — including for a marker-only "invalid"
     * deck item.
     * <p>
     * Requiring unique symbols (not just unique ids) matters because the pool of valid
     * symbols of a deck type is exactly {@link DeckType#getDeckSize()} in size: that many
     * distinct, individually valid symbols can only be the canonical full set. Without
     * this check, an item whose {@code custom_data} was tampered with externally (e.g. via
     * {@code /data modify} or another plugin) could pass as a "complete" deck while
     * actually containing duplicate symbols and missing others.
     */
    public static DeckItemData tryParseFullDeck(ItemStack item) {
        if (!hasTableGamesMarker(item)) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        Integer markerValue = pdc.get(PluginKeys.tableGames(), PersistentDataType.INTEGER);
        DeckType type = markerValue == null ? null : DeckType.fromMarkerValue(markerValue);
        String deckIdRaw = pdc.get(PluginKeys.deckId(), PersistentDataType.STRING);
        List<PersistentDataContainer> cardContainers = pdc.get(PluginKeys.cards(), PersistentDataType.LIST.dataContainers());

        if (type == null || deckIdRaw == null || cardContainers == null
                || cardContainers.size() != type.getDeckSize()) {
            return null;
        }

        UUID deckId;
        try {
            deckId = UUID.fromString(deckIdRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        List<Card> cards = new ArrayList<>(cardContainers.size());
        Set<UUID> seenIds = new HashSet<>();
        Set<String> seenSymbols = new HashSet<>();
        for (PersistentDataContainer cardPdc : cardContainers) {
            String cardIdRaw = cardPdc.get(PluginKeys.cardId(), PersistentDataType.STRING);
            String symbol = cardPdc.get(PluginKeys.symbol(), PersistentDataType.STRING);
            if (cardIdRaw == null || symbol == null || !type.isValidSymbol(symbol)) {
                return null;
            }
            UUID cardId;
            try {
                cardId = UUID.fromString(cardIdRaw);
            } catch (IllegalArgumentException ex) {
                return null;
            }
            if (!seenIds.add(cardId)) {
                return null; // duplicate card_id -> not a valid complete deck
            }
            if (!seenSymbols.add(symbol)) {
                return null; // duplicate symbol -> not a valid complete deck, even if ids differ
            }
            cards.add(new Card(cardId, deckId, symbol));
        }

        return new DeckItemData(deckId, type, cards);
    }

    public static final class DeckItemData {
        private final UUID deckId;
        private final DeckType type;
        private final List<Card> cards;

        public DeckItemData(UUID deckId, DeckType type, List<Card> cards) {
            this.deckId = deckId;
            this.type = type;
            this.cards = cards;
        }

        public UUID getDeckId() {
            return deckId;
        }

        public DeckType getType() {
            return type;
        }

        /**
         * Ordered bottom (index 0) to top (last index).
         */
        public List<Card> getCards() {
            return cards;
        }
    }
}