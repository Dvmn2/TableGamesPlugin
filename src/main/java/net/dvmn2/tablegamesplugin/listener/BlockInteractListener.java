package net.dvmn2.tablegamesplugin.listener;

import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.TableGamesPlugin;
import net.dvmn2.tablegamesplugin.manager.CardItemFactory;
import net.dvmn2.tablegamesplugin.manager.DeckItemFactory;
import net.dvmn2.tablegamesplugin.manager.DeckManager;
import net.dvmn2.tablegamesplugin.model.ActiveDeck;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.util.InteractionGuard;
import net.dvmn2.tablegamesplugin.util.PluginSounds;
import net.dvmn2.tablegamesplugin.util.SoundUtil;
import org.bukkit.Bukkit;
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
 * Handles every mechanic driven by right-clicking a block with the main hand:
 * <ul>
 *     <li>Placing a fresh/full deck item on top of a block (creates a physical deck)</li>
 *     <li>Shift + right-click a deck item to shuffle it (whether placed or not)</li>
 *     <li>Right-click with a held card near its active deck: reveal it face-up on the table,
 *     with the yaw of the player</li>
 *     <li>Shift + right-click with a held card near its active deck: put it on the table
 *     face-down, with the yaw of the player</li>
 * </ul>
 * Stacking a card onto an already-placed one is handled by {@link EntityInteractListener}.
 * Only the main hand is ever considered (specification section 29), and every branch is
 * guarded against double-firing (specification section 7).
 */
public final class BlockInteractListener implements Listener {

    private static final double REVEAL_RADIUS = 3.0;

    /**
     * How often stale {@link InteractionGuard} entries are swept away.
     */
    private static final long GUARD_SWEEP_INTERVAL_TICKS = 20L * 60; // once a minute

    private final TableGamesPlugin plugin;
    private final DeckManager deckManager;
    private final InteractionGuard guard = new InteractionGuard();

    public BlockInteractListener(TableGamesPlugin plugin, DeckManager deckManager) {
        this.plugin = plugin;
        this.deckManager = deckManager;

        // guard's keys are per-player + per-action-type here (not per-entity), so growth
        // is much smaller than in EntityInteractListener, but sweeping stale entries costs
        // nothing and keeps both guards consistent.
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, guard::sweepStaleEntries, GUARD_SWEEP_INTERVAL_TICKS, GUARD_SWEEP_INTERVAL_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // Only main-hand interactions matter; ignore the off-hand duplicate.
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (player.isSneaking() && DeckItemFactory.hasTableGamesMarker(itemInHand)) {
            handleShuffle(event, player, itemInHand);
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || event.getBlockFace() != BlockFace.UP) {
            return; // Only the top surface of a block is a valid interaction point.
        }

        if (DeckItemFactory.hasTableGamesMarker(itemInHand)) {
            handleDeckPlacement(event, player, clickedBlock, itemInHand);
            return;
        }

        Card card = CardItemFactory.tryParseCard(itemInHand);
        if (card != null) {
            handleCardOnTable(event, player, clickedBlock, card);
        }
    }

    private void handleShuffle(PlayerInteractEvent event, Player player, ItemStack itemInHand) {
        DeckItemFactory.DeckItemData data = DeckItemFactory.tryParseFullDeck(itemInHand);
        if (data == null) {
            return; // Marker-only/invalid items are simply not shuffled.
        }
        if (!guard.tryConsume(player, "shuffle")) {
            return;
        }

        ItemStack shuffled = deckManager.shuffleDeckItem(player, data);
        player.getInventory().setItemInMainHand(shuffled);
        event.setCancelled(true);
        SoundUtil.play(player.getLocation(), PluginSounds.SHUFFLE_DECK);
    }

    private void handleDeckPlacement(PlayerInteractEvent event, Player player, Block clickedBlock, ItemStack itemInHand) {
        if (!guard.tryConsume(player, "place-deck")) {
            return;
        }
        event.setCancelled(true);

        DeckItemFactory.DeckItemData data = DeckItemFactory.tryParseFullDeck(itemInHand);
        if (data == null) {
            Lang.send(player, Lang.Key.INVALID_DECK_ITEM);
            return;
        }

        boolean created = deckManager.createPhysicalDeck(player, clickedBlock, data);
        if (created) {
            consumeOneItem(player);
        }
    }

    private void handleCardOnTable(PlayerInteractEvent event, Player player, Block clickedBlock, Card card) {
        if (!guard.tryConsume(player, "table-card")) {
            return;
        }

        // Stacking a card onto an already-placed one is handled entirely by
        // EntityInteractListener: every card on the table has an Interaction hitbox
        // covering it (see DisplayFactory), so a click that lands on one is delivered as
        // a PlayerInteractEntityEvent and never reaches this block-click handler at all.
        // What's left here is only the case of putting a brand-new card near its deck.
        Location clickPoint = resolveClickPoint(event, clickedBlock);

        // The card's own deck, and only if it is close enough; a card is never adopted by
        // some other deck that merely happens to stand nearby.
        ActiveDeck deck = deckManager.getDeckWithinRange(card.getDeckId(), clickPoint, REVEAL_RADIUS);
        if (deck == null) {
            return;
        }

        event.setCancelled(true);
        // Either way the card looks the same way the player is looking right now.
        float playerYaw = player.getLocation().getYaw();
        if (player.isSneaking()) {
            deckManager.placeCardFaceDown(deck, clickedBlock, clickPoint, card, playerYaw);
        } else {
            deckManager.revealCard(deck, clickedBlock, clickPoint, card, playerYaw);
        }
        consumeOneItem(player);
    }

    private Location resolveClickPoint(PlayerInteractEvent event, Block block) {
        // getInteractionPoint() gives the exact absolute world position of the click;
        // fall back to the block's top-surface center if it is unexpectedly unavailable.
        Location exact = event.getInteractionPoint();
        return exact != null ? exact : block.getLocation().add(0.5, 1.0, 0.5);
    }

    private void consumeOneItem(Player player) {
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            current.setAmount(current.getAmount() - 1);
        }
    }
}