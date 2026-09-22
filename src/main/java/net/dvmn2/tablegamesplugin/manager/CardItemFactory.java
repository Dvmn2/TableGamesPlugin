package net.dvmn2.tablegamesplugin.manager;

import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.util.CardSymbols;
import net.dvmn2.tablegamesplugin.util.PluginKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * Builds and parses the {@code minecraft:filled_map} ItemStack representing a single
 * playing card in a player's inventory (specification sections 12-13).
 */
public final class CardItemFactory {

    private CardItemFactory() {
    }

    public static ItemStack createCardItem(Card card) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = item.getItemMeta();

        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setStrings(List.of("Playing Card"));
        meta.setCustomModelDataComponent(component);

        meta.displayName(Component.text(card.getSymbol()).decoration(TextDecoration.ITALIC, false));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(PluginKeys.cardId(), PersistentDataType.STRING, card.getCardId().toString());
        pdc.set(PluginKeys.deckId(), PersistentDataType.STRING, card.getDeckId().toString());
        pdc.set(PluginKeys.symbol(), PersistentDataType.STRING, card.getSymbol());

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCardItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(PluginKeys.cardId(), PersistentDataType.STRING)
                && pdc.has(PluginKeys.deckId(), PersistentDataType.STRING)
                && pdc.has(PluginKeys.symbol(), PersistentDataType.STRING);
    }

    /**
     * @return the {@link Card} encoded in the given item, or {@code null} if the item is
     * not a valid, recognizable card item.
     */
    public static Card tryParseCard(ItemStack item) {
        if (!isCardItem(item)) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String cardIdRaw = pdc.get(PluginKeys.cardId(), PersistentDataType.STRING);
        String deckIdRaw = pdc.get(PluginKeys.deckId(), PersistentDataType.STRING);
        String symbol = pdc.get(PluginKeys.symbol(), PersistentDataType.STRING);

        if (cardIdRaw == null || deckIdRaw == null || symbol == null || !CardSymbols.isValidSymbol(symbol)) {
            return null;
        }
        try {
            UUID cardId = UUID.fromString(cardIdRaw);
            UUID deckId = UUID.fromString(deckIdRaw);
            return new Card(cardId, deckId, symbol);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
