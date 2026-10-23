package net.dvmn2.tablegamesplugin;

import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.DeckItemData;
import net.dvmn2.tablegamesplugin.model.DeckType;
import net.dvmn2.tablegamesplugin.util.Uuids;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds and parses the deck item and its custom data:
 * <pre>
 * table_games: 0 | 1                  // 0 = poker (52 cards), 1 = durak (36 cards)
 * deck_id:     UUID string
 * cards:       [ { card_id: UUID string, symbol: string }, ... ]   // bottom to top
 * </pre>
 * An item carrying only the {@code table_games} marker is an invalid deck and is never accepted.
 * Thread: main server thread.
 */
public final class DeckItemFactory {

    private final PluginKeys keys;

    public DeckItemFactory(PluginKeys keys) {
        this.keys = keys;
    }

    /** Creates a brand-new deck item with fresh card identities in canonical order. */
    public ItemStack createNewDeckItem(DeckType type, CommandSender viewer) {
        UUID deckId = UUID.randomUUID();
        List<Card> cards = new ArrayList<>(type.getDeckSize());
        for (String symbol : type.initialOrder()) {
            cards.add(new Card(UUID.randomUUID(), deckId, symbol));
        }
        return createFullDeckItem(deckId, type, cards, viewer);
    }

    /**
     * Creates a deck item with the given card order.
     *
     * @param viewer whose language the item name is written in; the name is baked into the item
     */
    public ItemStack createFullDeckItem(UUID deckId, DeckType type, List<Card> orderedCards, CommandSender viewer) {
        ItemStack item = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = item.getItemMeta();

        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setStrings(List.of(type.getModelString()));
        meta.setCustomModelDataComponent(component);

        meta.displayName(Lang.get(type.getNameKey(), viewer).decoration(TextDecoration.ITALIC, false));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.tableGames(), PersistentDataType.INTEGER, type.getMarkerValue());
        pdc.set(keys.deckId(), PersistentDataType.STRING, deckId.toString());

        List<PersistentDataContainer> cardContainers = new ArrayList<>(orderedCards.size());
        for (Card card : orderedCards) {
            PersistentDataContainer cardPdc = pdc.getAdapterContext().newPersistentDataContainer();
            cardPdc.set(keys.cardId(), PersistentDataType.STRING, card.cardId().toString());
            cardPdc.set(keys.symbol(), PersistentDataType.STRING, card.symbol());
            cardContainers.add(cardPdc);
        }
        pdc.set(keys.cards(), PersistentDataType.LIST.dataContainers(), cardContainers);

        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} carries the deck marker, valid or not. */
    public boolean hasMarker(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(keys.tableGames(), PersistentDataType.INTEGER);
    }

    /**
     * Returns the deck encoded in {@code item}, or {@code null} unless it is complete and valid: a
     * known type, a deck id, exactly as many well-formed cards as that type has, with unique ids
     * and unique symbols, all valid for the type.
     * <p>
     * Unique symbols matter, not only unique ids: the set of valid symbols of a type has exactly the
     * deck's size, so that many distinct valid symbols can only be the canonical set. Without the
     * check, an item edited externally (for example with {@code /data modify}) could pass as a
     * complete deck while holding duplicate symbols and missing others.
     */
    @Nullable
    public DeckItemData tryParseFullDeck(@Nullable ItemStack item) {
        if (!hasMarker(item)) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        Integer markerValue = pdc.get(keys.tableGames(), PersistentDataType.INTEGER);
        DeckType type = markerValue == null ? null : DeckType.fromMarkerValue(markerValue);
        UUID deckId = Uuids.parseOrNull(pdc.get(keys.deckId(), PersistentDataType.STRING));
        List<PersistentDataContainer> cardContainers =
                pdc.get(keys.cards(), PersistentDataType.LIST.dataContainers());
        if (type == null || deckId == null || cardContainers == null || cardContainers.size() != type.getDeckSize()) {
            return null;
        }

        List<Card> cards = new ArrayList<>(cardContainers.size());
        Set<UUID> seenIds = new HashSet<>();
        Set<String> seenSymbols = new HashSet<>();
        for (PersistentDataContainer cardPdc : cardContainers) {
            UUID cardId = Uuids.parseOrNull(cardPdc.get(keys.cardId(), PersistentDataType.STRING));
            String symbol = cardPdc.get(keys.symbol(), PersistentDataType.STRING);
            if (cardId == null || !type.isValidSymbol(symbol) || !seenIds.add(cardId) || !seenSymbols.add(symbol)) {
                return null;
            }
            cards.add(new Card(cardId, deckId, symbol));
        }
        return new DeckItemData(deckId, type, cards);
    }
}
