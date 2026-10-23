package net.dvmn2.tablegamesplugin.listener;

import net.dvmn2.tablegamesplugin.CardItemFactory;
import net.dvmn2.tablegamesplugin.DeckItemFactory;
import net.dvmn2.tablegamesplugin.DeckManager;
import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.PluginKeys;
import net.dvmn2.tablegamesplugin.model.ActiveDeck;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.EntityRole;
import net.dvmn2.tablegamesplugin.model.RevealedCardInstance;
import net.dvmn2.tablegamesplugin.util.InteractionGuard;
import net.dvmn2.tablegamesplugin.util.PlayerItems;
import net.dvmn2.tablegamesplugin.util.Uuids;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Handles every mechanic driven by right-clicking one of the plugin's click hitboxes.
 * <ul>
 *     <li>Main pile and discard pile, same controls for both:
 *     <ul>
 *         <li>empty hand: take the top card</li>
 *         <li>shift + empty hand: pick the whole deck up; possible only while every card is in this
 *         pile, otherwise a message is shown and nothing is taken</li>
 *         <li>a card in hand: put it into a random place of the pile (discard) or an inner place (main)</li>
 *         <li>shift + a card in hand: put it on top of the pile</li>
 *     </ul></li>
 *     <li>Card on the table, always the top of its stack:
 *     <ul>
 *         <li>empty hand: take it</li>
 *         <li>shift + empty hand: flip the whole stack</li>
 *         <li>a card of the same deck in hand: stack it with a yaw offset</li>
 *         <li>shift + a card of the same deck in hand: stack it with a position offset and the same yaw</li>
 *     </ul></li>
 * </ul>
 * Thread: main server thread.
 */
public final class EntityInteractListener implements Listener {

    private final DeckManager manager;
    private final DeckItemFactory deckItems;
    private final CardItemFactory cardItems;
    private final PluginKeys keys;
    private final InteractionGuard guard;

    public EntityInteractListener(DeckManager manager, DeckItemFactory deckItems, CardItemFactory cardItems,
                                  PluginKeys keys, InteractionGuard guard) {
        this.manager = manager;
        this.deckItems = deckItems;
        this.cardItems = cardItems;
        this.keys = keys;
        this.guard = guard;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        // PlayerInteractAtEntityEvent is a subclass fired for the same click; handle only the base event.
        if (event instanceof PlayerInteractAtEntityEvent || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity target = event.getRightClicked();
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        EntityRole role = EntityRole.fromId(pdc.get(keys.type(), PersistentDataType.STRING));
        UUID deckId = Uuids.parseOrNull(pdc.get(keys.deckId(), PersistentDataType.STRING));
        if (role == null || deckId == null) {
            return; // not one of our entities
        }
        ActiveDeck deck = manager.getDeck(deckId);
        if (deck == null) {
            return; // the entity outlived its deck
        }
        Player player = event.getPlayer();
        if (!guard.tryConsume(player, "entity-" + target.getUniqueId())) {
            return;
        }

        switch (role) {
            case DECK -> handleMainPile(event, player, deck);
            case DISCARD -> handleDiscardPile(event, player, deck);
            case REVEALED_CARD -> handleRevealedCard(event, player, deck, pdc);
            case TRUMP -> {
                // The trump display is not interactive.
            }
        }
    }

    private void handleMainPile(PlayerInteractEntityEvent event, Player player, ActiveDeck deck) {
        event.setCancelled(true);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            if (player.isSneaking()) {
                if (manager.collectFullDeckFromMain(player, deck) == DeckManager.CollectResult.NOT_COLLECTED) {
                    Lang.send(player, Lang.Key.DECK_NOT_COLLECTED_IN_MAIN);
                }
            } else if (manager.giveTopCard(player, deck) == DeckManager.TakeResult.EMPTY) {
                Lang.send(player, Lang.Key.DECK_EMPTY);
            }
            return;
        }
        Card card = cardItems.tryParseCard(itemInHand);
        if (card == null) {
            return; // holding something unrelated
        }
        DeckManager.MoveResult result = player.isSneaking()
                ? manager.putCardOnTopOfDeck(deck, card)
                : manager.returnCardToDeck(deck, card);
        finishMove(player, result);
    }

    private void handleDiscardPile(PlayerInteractEntityEvent event, Player player, ActiveDeck deck) {
        event.setCancelled(true);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            if (player.isSneaking()) {
                if (manager.collectFullDeckFromDiscard(player, deck) == DeckManager.CollectResult.NOT_COLLECTED) {
                    Lang.send(player, Lang.Key.DECK_NOT_COLLECTED_IN_DISCARD);
                }
            } else {
                manager.takeTopFromDiscard(player, deck); // an empty discard pile is silent
            }
            return;
        }
        Card card = cardItems.tryParseCard(itemInHand);
        if (card == null) {
            return;
        }
        DeckManager.MoveResult result = player.isSneaking()
                ? manager.addCardToDiscard(deck, card)
                : manager.insertCardIntoDiscardRandomly(deck, card);
        finishMove(player, result);
    }

    private void handleRevealedCard(PlayerInteractEntityEvent event, Player player, ActiveDeck deck,
                                    PersistentDataContainer pdc) {
        UUID cardId = Uuids.parseOrNull(pdc.get(keys.cardId(), PersistentDataType.STRING));
        if (cardId == null) {
            return;
        }
        RevealedCardInstance instance = deck.findRevealed(cardId);
        if (instance == null) {
            return;
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                manager.flipStack(deck, instance);
            } else {
                manager.takeRevealedCard(player, deck, instance);
            }
            return;
        }
        Card heldCard = cardItems.tryParseCard(itemInHand);
        if (heldCard == null) {
            if (deckItems.hasMarker(itemInHand)) {
                event.setCancelled(true); // a deck item on a table card does nothing, not even a shuffle
            }
            return;
        }
        event.setCancelled(true);
        finishMove(player, manager.stackCard(deck, instance, heldCard, player.isSneaking()));
    }

    private void finishMove(Player player, DeckManager.MoveResult result) {
        switch (result) {
            case SUCCESS -> PlayerItems.consumeOneFromMainHand(player);
            case WRONG_DECK -> Lang.send(player, Lang.Key.WRONG_DECK);
        }
    }
}
