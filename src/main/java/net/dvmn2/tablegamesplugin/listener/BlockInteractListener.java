package net.dvmn2.tablegamesplugin.listener;

import net.dvmn2.tablegamesplugin.CardItemFactory;
import net.dvmn2.tablegamesplugin.DeckItemFactory;
import net.dvmn2.tablegamesplugin.DeckManager;
import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.model.ActiveDeck;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.DeckItemData;
import net.dvmn2.tablegamesplugin.util.InteractionGuard;
import net.dvmn2.tablegamesplugin.util.PlayerItems;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles every mechanic driven by right-clicking with the main hand:
 * <ul>
 *     <li>placing a deck item on the top of a block creates a physical deck</li>
 *     <li>shift + right click with a deck item shuffles it</li>
 *     <li>right click with a card near its deck puts it on the table face up, looking the way the
 *     player looks</li>
 *     <li>shift + right click with a card near its deck puts it on the table face down</li>
 * </ul>
 * Stacking onto a card that is already on the table is handled by {@link EntityInteractListener}: every
 * table card has a hitbox, so such a click arrives as an entity interaction and never reaches this class.
 * Thread: main server thread.
 */
public final class BlockInteractListener implements Listener {

    private static final String ACTION_SHUFFLE = "shuffle";
    private static final String ACTION_PLACE_DECK = "place-deck";
    private static final String ACTION_TABLE_CARD = "table-card";

    private final DeckManager manager;
    private final DeckItemFactory deckItems;
    private final CardItemFactory cardItems;
    private final InteractionGuard guard;

    public BlockInteractListener(DeckManager manager, DeckItemFactory deckItems, CardItemFactory cardItems,
                                 InteractionGuard guard) {
        this.manager = manager;
        this.deckItems = deckItems;
        this.cardItems = cardItems;
        this.guard = guard;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // the off-hand fires a duplicate event
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (player.isSneaking() && deckItems.hasMarker(itemInHand)) {
            handleShuffle(event, player, itemInHand);
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || event.getBlockFace() != BlockFace.UP) {
            return; // only the top surface of a block is a valid interaction point
        }
        if (deckItems.hasMarker(itemInHand)) {
            handleDeckPlacement(event, player, clickedBlock, itemInHand);
            return;
        }
        Card card = cardItems.tryParseCard(itemInHand);
        if (card != null) {
            handleCardOnTable(event, player, clickedBlock, card);
        }
    }

    private void handleShuffle(PlayerInteractEvent event, Player player, ItemStack itemInHand) {
        DeckItemData data = deckItems.tryParseFullDeck(itemInHand);
        if (data == null || !guard.tryConsume(player, ACTION_SHUFFLE)) {
            return; // marker-only or damaged items are simply not shuffled
        }
        player.getInventory().setItemInMainHand(manager.shuffleDeckItem(player, data));
        event.setCancelled(true);
    }

    private void handleDeckPlacement(PlayerInteractEvent event, Player player, Block clickedBlock,
                                     ItemStack itemInHand) {
        if (!guard.tryConsume(player, ACTION_PLACE_DECK)) {
            return;
        }
        event.setCancelled(true);

        DeckItemData data = deckItems.tryParseFullDeck(itemInHand);
        if (data == null) {
            Lang.send(player, Lang.Key.INVALID_DECK_ITEM);
            return;
        }
        switch (manager.createPhysicalDeck(clickedBlock, data)) {
            case SUCCESS -> PlayerItems.consumeOneFromMainHand(player);
            case ALREADY_ACTIVE -> Lang.send(player, Lang.Key.DECK_ALREADY_ACTIVE);
            case NO_CLEAR_SURFACE -> Lang.send(player, Lang.Key.NO_CLEAR_SURFACE);
        }
    }

    private void handleCardOnTable(PlayerInteractEvent event, Player player, Block clickedBlock, Card card) {
        if (!guard.tryConsume(player, ACTION_TABLE_CARD)) {
            return;
        }
        Location clickPoint = resolveClickPoint(event, clickedBlock);

        // Only the card's own deck, and only nearby: a card is never adopted by another deck that merely
        // stands close by.
        ActiveDeck deck = manager.getDeckWithinRange(card.deckId(), clickPoint);
        if (deck == null) {
            return;
        }
        event.setCancelled(true);
        float playerYaw = player.getLocation().getYaw();
        if (player.isSneaking()) {
            manager.placeCardFaceDown(deck, clickedBlock, clickPoint, card, playerYaw);
        } else {
            manager.revealCard(deck, clickedBlock, clickPoint, card, playerYaw);
        }
        PlayerItems.consumeOneFromMainHand(player);
    }

    private Location resolveClickPoint(PlayerInteractEvent event, Block block) {
        // The interaction point is the exact click position; fall back to the surface centre if absent.
        Location exact = event.getInteractionPoint();
        return exact != null ? exact : block.getLocation().add(0.5, 1.0, 0.5);
    }
}
