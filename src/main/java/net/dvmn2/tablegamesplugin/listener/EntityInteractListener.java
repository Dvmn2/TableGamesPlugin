package net.dvmn2.tablegamesplugin.listener;

import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.TableGamesPlugin;
import net.dvmn2.tablegamesplugin.manager.CardItemFactory;
import net.dvmn2.tablegamesplugin.manager.DeckManager;
import net.dvmn2.tablegamesplugin.model.ActiveDeck;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.RevealedCardInstance;
import net.dvmn2.tablegamesplugin.util.InteractionGuard;
import net.dvmn2.tablegamesplugin.util.PluginKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

/**
 * Handles every mechanic driven by right-clicking directly on one of the plugin's
 * clickable (Interaction) entities:
 * <ul>
 *     <li>Deck (main pile): take the top card (empty hand), return a held card to a random
 *     internal position, or shift + empty hand to pick the whole deck up &mdash; only
 *     possible while every card of the deck is in the main pile (otherwise shift is
 *     ignored and the top card is taken as usual)</li>
 *     <li>Discard pile: take the top card (empty hand), place a held card on top, or
 *     shift + empty hand to collect the full deck</li>
 *     <li>Card on the table (always the top of its stack):
 *     <ul>
 *         <li>empty hand: take it</li>
 *         <li>shift + empty hand on a face-down stack: reveal the whole stack (turn all its
 *         cards face-up); on a face-up card shift changes nothing, it is taken</li>
 *         <li>a card of the same deck in hand: stack it on top, keeping the orientation of
 *         the stack (face-down onto face-down, face-up onto face-up)</li>
 *     </ul></li>
 * </ul>
 * Only the main hand is ever considered (specification section 29), and every branch is
 * guarded against double-firing (specification section 7).
 */
public final class EntityInteractListener implements Listener {

    /**
     * How often stale {@link InteractionGuard} entries are swept away.
     */
    private static final long GUARD_SWEEP_INTERVAL_TICKS = 20L * 60; // once a minute

    private final TableGamesPlugin plugin;
    private final DeckManager deckManager;
    private final InteractionGuard guard = new InteractionGuard();

    public EntityInteractListener(TableGamesPlugin plugin, DeckManager deckManager) {
        this.plugin = plugin;
        this.deckManager = deckManager;

        // guard's keys include per-entity ids (revealed cards, decks, ...), which are
        // effectively unbounded over a server's lifetime; sweep periodically so it never
        // grows without limit. ConcurrentHashMap makes this safe to run off the main thread.
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, guard::sweepStaleEntries, GUARD_SWEEP_INTERVAL_TICKS, GUARD_SWEEP_INTERVAL_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // Only main-hand interactions matter; ignore the off-hand duplicate.
        }

        Entity target = event.getRightClicked();
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        String type = pdc.get(PluginKeys.type(), PersistentDataType.STRING);
        if (type == null) {
            return; // Not one of our entities.
        }

        String deckIdRaw = pdc.get(PluginKeys.deckId(), PersistentDataType.STRING);
        UUID deckId = parseUuidOrNull(deckIdRaw);
        if (deckId == null) {
            return;
        }

        ActiveDeck deck = deckManager.getDeck(deckId);
        if (deck == null) {
            return; // Entity exists but its logical deck is no longer active.
        }

        Player player = event.getPlayer();
        if (!guard.tryConsume(player, "entity-" + target.getUniqueId())) {
            return;
        }

        switch (type) {
            case PluginKeys.TYPE_DECK -> handleDeckInteract(event, player, deck);
            case PluginKeys.TYPE_DISCARD -> handleDiscardInteract(event, player, deck);
            case PluginKeys.TYPE_REVEALED_CARD -> handleRevealedCardInteract(event, player, deck, pdc);
            default -> {
                // The trump TextDisplay has no interactive behaviour of its own.
            }
        }
    }

    private void handleDeckInteract(PlayerInteractEntityEvent event, Player player, ActiveDeck deck) {
        event.setCancelled(true);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            if (player.isSneaking()) {
                // Only succeeds while ALL cards are in the main pile; otherwise fall
                // through and behave exactly as before (deal the top card).
                ItemStack assembled = deckManager.collectFullDeckFromMain(player, deck);
                if (assembled != null) {
                    giveOrDrop(player, assembled);
                    Lang.send(player, Lang.Key.DECK_COLLECTED);
                    return;
                }
            }
            deckManager.giveTopCard(player, deck);
            return;
        }

        Card card = CardItemFactory.tryParseCard(itemInHand);
        if (card == null) {
            return; // Holding something irrelevant; ignore.
        }

        if (!card.getDeckId().equals(deck.getDeckId())) {
            Lang.send(player, Lang.Key.WRONG_DECK);
            return;
        }

        deckManager.returnCardToDeck(deck, card);
        consumeOneItem(player);
    }

    private void handleDiscardInteract(PlayerInteractEntityEvent event, Player player, ActiveDeck deck) {
        event.setCancelled(true);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            if (player.isSneaking()) {
                handleCollectFromDiscard(player, deck);
            } else {
                Card top = deckManager.takeTopFromDiscard(deck);
                if (top != null) {
                    giveOrDrop(player, CardItemFactory.createCardItem(top));
                }
            }
            return;
        }

        Card card = CardItemFactory.tryParseCard(itemInHand);
        if (card == null) {
            return; // Holding something irrelevant; ignore.
        }

        if (!card.getDeckId().equals(deck.getDeckId())) {
            Lang.send(player, Lang.Key.WRONG_DECK);
            return;
        }

        deckManager.addCardToDiscard(deck, card);
        consumeOneItem(player);
    }

    private void handleCollectFromDiscard(Player player, ActiveDeck deck) {
        ItemStack assembled = deckManager.collectFullDeckFromDiscard(player, deck);
        if (assembled != null) {
            giveOrDrop(player, assembled);
            Lang.send(player, Lang.Key.DECK_COLLECTED);
        } else {
            Lang.send(player, Lang.Key.DECK_NOT_COLLECTED_IN_DISCARD);
        }
    }

    /**
     * A table card's clickable hitbox is always the current top of its stack (see
     * {@code DeckManager#stackCard}/{@code #takeRevealedCard}), so a click here with an
     * empty hand takes that top card (or, with shift, reveals a face-down stack), and a
     * click with another card of the same deck in hand stacks it on top instead.
     */
    private void handleRevealedCardInteract(PlayerInteractEntityEvent event, Player player, ActiveDeck deck, PersistentDataContainer pdc) {
        UUID cardId = parseUuidOrNull(pdc.get(PluginKeys.cardId(), PersistentDataType.STRING));
        if (cardId == null) {
            return;
        }

        RevealedCardInstance instance = null;
        for (RevealedCardInstance candidate : deck.getRevealedCards()) {
            if (candidate.getCard().getCardId().equals(cardId)) {
                instance = candidate;
                break;
            }
        }
        if (instance == null) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            event.setCancelled(true);
            if (player.isSneaking() && instance.isFaceDown()) {
                deckManager.revealStack(deck, instance);
                return;
            }
            deckManager.takeRevealedCard(deck, instance);
            giveOrDrop(player, CardItemFactory.createCardItem(instance.getCard()));
            return;
        }

        Card heldCard = CardItemFactory.tryParseCard(itemInHand);
        if (heldCard == null) {
            return; // Holding something irrelevant; ignore.
        }

        if (!heldCard.getDeckId().equals(deck.getDeckId())) {
            Lang.send(player, Lang.Key.WRONG_DECK);
            return;
        }

        event.setCancelled(true);
        deckManager.stackCard(deck, instance, heldCard);
        consumeOneItem(player);
    }

    private UUID parseUuidOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void consumeOneItem(Player player) {
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            current.setAmount(current.getAmount() - 1);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}