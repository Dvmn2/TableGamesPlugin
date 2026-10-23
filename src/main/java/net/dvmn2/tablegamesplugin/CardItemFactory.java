package net.dvmn2.tablegamesplugin;

import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.util.CardSymbols;
import net.dvmn2.tablegamesplugin.util.Uuids;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Builds and parses the {@code filled_map} item that represents a single playing card in an
 * inventory. Thread: main server thread.
 */
public final class CardItemFactory {

    /** The custom-model-data string the resource pack maps to the card model; also used by table displays. */
    public static final String MODEL_STRING = "Playing Card";

    private final PluginKeys keys;

    public CardItemFactory(PluginKeys keys) {
        this.keys = keys;
    }

    public ItemStack createCardItem(Card card) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = item.getItemMeta();

        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setStrings(List.of(MODEL_STRING));
        meta.setCustomModelDataComponent(component);

        meta.displayName(Component.text(card.symbol()).decoration(TextDecoration.ITALIC, false));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.cardId(), PersistentDataType.STRING, card.cardId().toString());
        pdc.set(keys.deckId(), PersistentDataType.STRING, card.deckId().toString());
        pdc.set(keys.symbol(), PersistentDataType.STRING, card.symbol());

        item.setItemMeta(meta);
        return item;
    }

    /** Returns the card encoded in {@code item}, or {@code null} if it is not a valid card item. */
    @Nullable
    public Card tryParseCard(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        UUID cardId = Uuids.parseOrNull(pdc.get(keys.cardId(), PersistentDataType.STRING));
        UUID deckId = Uuids.parseOrNull(pdc.get(keys.deckId(), PersistentDataType.STRING));
        String symbol = pdc.get(keys.symbol(), PersistentDataType.STRING);
        if (cardId == null || deckId == null || !CardSymbols.isValidSymbol(symbol)) {
            return null;
        }
        return new Card(cardId, deckId, symbol);
    }
}
