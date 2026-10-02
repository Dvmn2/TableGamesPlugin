package net.dvmn2.tablegamesplugin.manager;

import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.TableGamesPlugin;
import net.dvmn2.tablegamesplugin.display.DisplayFactory;
import net.dvmn2.tablegamesplugin.model.ActiveDeck;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.RevealedCardInstance;
import net.dvmn2.tablegamesplugin.storage.StorageManager;
import net.dvmn2.tablegamesplugin.storage.dto.StorageFileDto;
import net.dvmn2.tablegamesplugin.util.PluginKeys;
import net.dvmn2.tablegamesplugin.util.PluginSounds;
import net.dvmn2.tablegamesplugin.util.SoundUtil;
import net.dvmn2.tablegamesplugin.util.TransformUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central authority for all active physical decks: creation, shuffling, dealing,
 * discarding, revealing/stacking cards on the table, and full-deck reassembly.
 * Delegates entity creation to {@link DisplayFactory} and persistence to
 * {@link StorageManager}.
 */
public final class DeckManager {

    public enum MoveResult {
        SUCCESS,
        WRONG_DECK
    }

    /**
     * Vertical distance between two cards of the same table stack (avoids z-fighting).
     */
    private static final double STACK_LAYER_STEP = 0.001;

    /**
     * How far a card stacked in "even" mode (shift + click) is shifted from the one beneath
     * it, along the direction the cards "look" (the yaw of the card beneath). Tweak this to
     * make such piles look thicker/thinner.
     */
    private static final double STACK_POSITION_OFFSET = 0.1;

    private final TableGamesPlugin plugin;
    private final StorageManager storageManager;
    private final DisplayFactory displayFactory;
    private final Random random = new Random();

    private final Map<UUID, ActiveDeck> decks = new ConcurrentHashMap<>();

    /**
     * Set whenever any deck's logical state changes. A periodic task (see
     * {@link #flushIfDirty()}, scheduled by the plugin) drains this flag instead of
     * every mutation blocking the main thread with a synchronous disk write.
     */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public DeckManager(TableGamesPlugin plugin) {
        this.plugin = plugin;
        this.storageManager = new StorageManager(plugin);
        this.displayFactory = new DisplayFactory(plugin);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void loadState() {
        for (ActiveDeck deck : storageManager.loadAll()) {
            decks.put(deck.getDeckId(), deck);
            relinkEntities(deck);
        }
        plugin.getLogger().info("Loaded " + decks.size() + " active table game deck(s).");
    }

    /**
     * Synchronous, blocking full save. Used at plugin disable, where we must guarantee
     * the write completes before shutdown proceeds. For ordinary in-game mutations,
     * prefer {@link #markDirty()} + the periodic {@link #flushIfDirty()} task instead of
     * calling this directly, since this blocks the calling thread on disk I/O.
     */
    public void saveState() {
        storageManager.saveAll(decks.values());
        dirty.set(false);
    }

    /**
     * Marks deck state as changed since the last successful save.
     */
    private void markDirty() {
        dirty.set(true);
    }

    /**
     * Intended to be called periodically on the main thread (see
     * {@code TableGamesPlugin#onEnable()}). If state has changed since the last flush,
     * takes a cheap in-memory snapshot synchronously (safe, since it happens on the main
     * thread alongside every mutation of the {@link ActiveDeck} lists) and then hands the
     * slow part &mdash; the actual disk write &mdash; off to an async task, so that bursts
     * of card interactions never block the server thread on file I/O.
     * <p>
     * Trade-off: up to one flush interval's worth of the most recent changes can be lost
     * on an unclean crash (a normal {@code /stop} or plugin disable is unaffected, since
     * {@link #saveState()} is called synchronously there).
     */
    public void flushIfDirty() {
        if (!dirty.compareAndSet(true, false)) {
            return;
        }
        StorageFileDto snapshot = storageManager.buildSnapshot(decks.values());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storageManager.writeSnapshot(snapshot));
    }

    private void relinkEntities(ActiveDeck deck) {
        World world = Bukkit.getWorld(deck.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("World '" + deck.getWorldName() + "' for deck "
                    + deck.getDeckId() + " is not currently loaded; its entities could not be verified.");
            return;
        }

        world.getChunkAt(deck.getAnchorBlockX() >> 4, deck.getAnchorBlockZ() >> 4).load();
        world.getChunkAt((deck.getAnchorBlockX() + 1) >> 4, deck.getAnchorBlockZ() >> 4).load();

        // Main pile entities are intentionally null once the pile has been fully dealt
        // out (see removeMainPileEntities()), and the trump display is always null for
        // deck types without a trump; verifyEntity() treats null as a no-op.
        verifyEntity(deck.getMainDisplayEntityId(), ItemDisplay.class);
        verifyEntity(deck.getDiscardDisplayEntityId(), ItemDisplay.class);
        verifyEntity(deck.getTrumpDisplayEntityId(), TextDisplay.class);
        verifyEntity(deck.getMainInteractionEntityId(), Interaction.class);
        verifyEntity(deck.getDiscardInteractionEntityId(), Interaction.class);

        for (RevealedCardInstance revealed : deck.getRevealedCards()) {
            Location loc = revealed.getLocation();
            if (loc.getWorld() != null) {
                loc.getWorld().getChunkAt(loc).load();
            }
            verifyEntity(revealed.getEntityUuid(), TextDisplay.class);
            // Only the top card of a stack carries a non-null interaction id (see
            // stackCard()/takeRevealedCard()); buried cards are visual-only, so a null id
            // here is expected, not a sign of a lost entity.
            verifyEntity(revealed.getInteractionEntityUuid(), Interaction.class);
        }
    }

    private void verifyEntity(UUID uuid, Class<? extends Entity> expectedType) {
        if (uuid == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null || !expectedType.isInstance(entity)) {
            // Specification section 28: missing display entities are never recreated automatically.
            plugin.getLogger().warning("Expected " + expectedType.getSimpleName() + " " + uuid
                    + " was not found; it will not be recreated automatically.");
        }
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public ActiveDeck getDeck(UUID deckId) {
        return decks.get(deckId);
    }

    public Collection<ActiveDeck> getAllDecks() {
        return Collections.unmodifiableCollection(decks.values());
    }

    /**
     * The active deck with the given id, but only if its main pile is within
     * {@code maxDistance} of {@code point}; otherwise {@code null}. A card can only be put
     * on the table near the deck it belongs to: a card of deck A must never be adopted by
     * some other deck B that merely happens to stand nearby.
     */
    public ActiveDeck getDeckWithinRange(UUID deckId, Location point, double maxDistance) {
        ActiveDeck deck = decks.get(deckId);
        if (deck == null) {
            return null;
        }
        Location center = deck.getAnchorTopSurfaceCenter();
        if (center == null || center.getWorld() == null || !center.getWorld().equals(point.getWorld())) {
            return null;
        }
        return center.distanceSquared(point) <= maxDistance * maxDistance ? deck : null;
    }

    /**
     * The top-most card of the given stack (highest Y; later list position wins a tie), or
     * {@code null} if the stack has no cards left. Stacks are identified by
     * {@link RevealedCardInstance#getStackId()} rather than by position, since face-down
     * stacks are shifted card by card.
     */
    private RevealedCardInstance findTopOfStack(ActiveDeck deck, UUID stackId) {
        RevealedCardInstance top = null;
        double bestY = -Double.MAX_VALUE;
        for (RevealedCardInstance candidate : deck.getRevealedCards()) {
            if (!candidate.getStackId().equals(stackId)) {
                continue;
            }
            double y = candidate.getLocation().getY();
            if (y >= bestY) {
                top = candidate;
                bestY = y;
            }
        }
        return top;
    }

    // ------------------------------------------------------------------
    // Deck creation & shuffling
    // ------------------------------------------------------------------

    /**
     * Shuffles the logical order of cards encoded in a held deck item, returning a new item.
     *
     * @param viewer the player holding the item (the new item's name uses their language)
     */
    public ItemStack shuffleDeckItem(Player viewer, DeckItemFactory.DeckItemData data) {
        List<Card> shuffled = new ArrayList<>(data.getCards());
        Collections.shuffle(shuffled, random);
        return DeckItemFactory.createFullDeckItem(data.getDeckId(), data.getType(), shuffled, viewer);
    }

    /**
     * Materializes a full deck item into a physical, active deck on top of the given block.
     *
     * @return {@code true} if the deck was created, {@code false} if a deck with this id is
     * already physically active, or if the target location does not have room for both the
     * main pile and discard pile displays (in which case the item is left untouched).
     */
    public boolean createPhysicalDeck(Player player, Block clickedBlock, DeckItemFactory.DeckItemData data) {
        if (decks.containsKey(data.getDeckId())) {
            Lang.send(player, Lang.Key.DECK_ALREADY_ACTIVE);
            return false;
        }

        // The discard pile display always sits one block over on the +X side (see the
        // fixed (1.5, 1.0, 0.5) offset below), regardless of player facing, so both this
        // block and its +X neighbour need a solid top with clear space above it.
        Block discardBlock = clickedBlock.getRelative(BlockFace.EAST);
        if (!hasClearSolidSurface(clickedBlock) || !hasClearSolidSurface(discardBlock)) {
            Lang.send(player, Lang.Key.NO_CLEAR_SURFACE);
            return false;
        }

        Location mainLoc = clickedBlock.getLocation().add(0.5, 1.0, 0.5);
        Location discardLoc = clickedBlock.getLocation().add(1.5, 1.0, 0.5);

        float mainYaw = TransformUtil.normalizeYaw(random.nextFloat() * 360f);
        float discardYaw = TransformUtil.normalizeYaw(random.nextFloat() * 360f);

        ItemDisplay mainDisplay = displayFactory.spawnCardItemDisplay(
                mainLoc, mainYaw, data.getDeckId(), PluginKeys.TYPE_DECK);
        ItemDisplay discardDisplay = displayFactory.spawnCardItemDisplay(
                discardLoc, discardYaw, data.getDeckId(), PluginKeys.TYPE_DISCARD);

        // Display entities have no interaction hitbox of their own — these Interaction
        // entities are what actually receives the player's right-click.
        Interaction mainInteraction = displayFactory.spawnPileInteraction(mainLoc, data.getDeckId(), PluginKeys.TYPE_DECK);
        Interaction discardInteraction = displayFactory.spawnPileInteraction(discardLoc, data.getDeckId(), PluginKeys.TYPE_DISCARD);

        ActiveDeck deck = new ActiveDeck(data.getDeckId(), data.getType(), data.getCards());
        deck.getMainPile().addAll(data.getCards());
        deck.setWorldName(clickedBlock.getWorld().getName());
        deck.setAnchorBlockX(clickedBlock.getX());
        deck.setAnchorBlockY(clickedBlock.getY());
        deck.setAnchorBlockZ(clickedBlock.getZ());
        deck.setMainDisplayEntityId(mainDisplay.getUniqueId());
        deck.setDiscardDisplayEntityId(discardDisplay.getUniqueId());
        deck.setMainInteractionEntityId(mainInteraction.getUniqueId());
        deck.setDiscardInteractionEntityId(discardInteraction.getUniqueId());
        deck.setMainDisplayYaw(mainYaw);
        deck.setDiscardDisplayYaw(discardYaw);

        // Only deck types with a trump (durak) show the bottom card; showing it in poker
        // would reveal a card that is supposed to stay hidden.
        if (deck.getType().hasTrump()) {
            TextDisplay trumpDisplay = displayFactory.spawnTrumpDisplay(
                    mainLoc.clone().add(0, 0.01, 0), mainYaw + 90f, data.getDeckId(), deck.getTrumpCard().getSymbol());
            deck.setTrumpDisplayEntityId(trumpDisplay.getUniqueId());
        }

        decks.put(deck.getDeckId(), deck);
        markDirty();
        SoundUtil.play(mainLoc, PluginSounds.PLACE_DECK);
        return true;
    }

    /**
     * A block a deck/discard display can sit on: solid on top, with clear space above it.
     */
    private boolean hasClearSolidSurface(Block block) {
        if (!block.getType().isSolid()) {
            return false;
        }
        return block.getRelative(BlockFace.UP).isPassable();
    }

    // ------------------------------------------------------------------
    // Main pile
    // ------------------------------------------------------------------

    /**
     * Gives the top card of the main pile to the player, if any remain.
     */
    public boolean giveTopCard(Player player, ActiveDeck deck) {
        List<Card> pile = deck.getMainPile();
        if (pile.isEmpty()) {
            Lang.send(player, Lang.Key.DECK_EMPTY);
            return false;
        }
        Card top = pile.remove(pile.size() - 1);
        giveOrDrop(player, CardItemFactory.createCardItem(top));
        playAtAnchor(deck, PluginSounds.TAKE_CARD_FROM_PILE);

        if (pile.isEmpty()) {
            // Nothing left to show or click on the main pile spot: remove the trump text,
            // the deck's item display, and its interaction hitbox together, rather than
            // just blanking the trump text and leaving two now-meaningless entities behind.
            removeMainPileEntities(deck);
        } else {
            updateTrumpDisplay(deck);
        }
        markDirty();
        return true;
    }

    private void removeMainPileEntities(ActiveDeck deck) {
        removeEntitySafely(deck.getTrumpDisplayEntityId());
        removeEntitySafely(deck.getMainDisplayEntityId());
        removeEntitySafely(deck.getMainInteractionEntityId());
        deck.setTrumpDisplayEntityId(null);
        deck.setMainDisplayEntityId(null);
        deck.setMainInteractionEntityId(null);
    }

    /**
     * Inserts a card back into the main pile at a random internal position (never at the
     * very bottom, and only at the very top if 0 or 1 cards remain).
     */
    public MoveResult returnCardToDeck(ActiveDeck deck, Card card) {
        if (!card.getDeckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        List<Card> pile = deck.getMainPile();
        int size = pile.size();
        int insertIndex = (size <= 1) ? size : 1 + random.nextInt(size - 1);
        pile.add(insertIndex, card);
        updateTrumpDisplay(deck);
        markDirty();
        playAtAnchor(deck, PluginSounds.PUT_CARD_INTO_PILE);
        return MoveResult.SUCCESS;
    }

    /**
     * Puts a card on top of the main pile (the next one to be dealt).
     */
    public MoveResult putCardOnTopOfDeck(ActiveDeck deck, Card card) {
        if (!card.getDeckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        deck.getMainPile().add(card);
        updateTrumpDisplay(deck);
        markDirty();
        playAtAnchor(deck, PluginSounds.PUT_CARD_INTO_PILE);
        return MoveResult.SUCCESS;
    }

    /**
     * Plays a sound at the main pile's anchor point, if its world is currently loaded.
     */
    private void playAtAnchor(ActiveDeck deck, String soundKey) {
        Location anchor = deck.getAnchorTopSurfaceCenter();
        if (anchor != null) {
            SoundUtil.play(anchor, soundKey);
        }
    }

    /**
     * Plays a sound at the discard pile's anchor point, if its world is currently loaded.
     */
    private void playAtDiscardAnchor(ActiveDeck deck, String soundKey) {
        Location anchor = deck.getDiscardTopSurfaceCenter();
        if (anchor != null) {
            SoundUtil.play(anchor, soundKey);
        }
    }

    private void updateTrumpDisplay(ActiveDeck deck) {
        UUID trumpId = deck.getTrumpDisplayEntityId();
        if (trumpId == null) {
            return; // deck type without a trump, or main pile already dealt out
        }
        Entity entity = Bukkit.getEntity(trumpId);
        if (entity instanceof TextDisplay textDisplay) {
            Card trump = deck.getTrumpCard();
            textDisplay.text(Component.text(trump == null ? "" : trump.getSymbol()));
        }
    }

    // ------------------------------------------------------------------
    // Discard pile
    // ------------------------------------------------------------------

    public MoveResult addCardToDiscard(ActiveDeck deck, Card card) {
        if (!card.getDeckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        deck.getDiscardPile().add(card);
        markDirty();
        playAtDiscardAnchor(deck, PluginSounds.PUT_CARD_INTO_PILE);
        return MoveResult.SUCCESS;
    }

    /**
     * Inserts a card into the discard pile at a random position (any index, including
     * the very top and the very bottom).
     */
    public MoveResult insertCardIntoDiscardRandomly(ActiveDeck deck, Card card) {
        if (!card.getDeckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        List<Card> discard = deck.getDiscardPile();
        discard.add(random.nextInt(discard.size() + 1), card);
        markDirty();
        playAtDiscardAnchor(deck, PluginSounds.PUT_CARD_INTO_PILE);
        return MoveResult.SUCCESS;
    }

    public Card takeTopFromDiscard(ActiveDeck deck) {
        List<Card> discard = deck.getDiscardPile();
        if (discard.isEmpty()) {
            return null;
        }
        Card top = discard.remove(discard.size() - 1);
        markDirty();
        playAtDiscardAnchor(deck, PluginSounds.TAKE_CARD_FROM_PILE);
        return top;
    }

    // ------------------------------------------------------------------
    // Reassembling the full deck
    // ------------------------------------------------------------------

    /**
     * Attempts to reassemble the full deck item from the discard pile.
     *
     * @return the assembled deck item, or {@code null} if the collection conditions
     * (specification sections 22-23) are not satisfied.
     */
    public ItemStack collectFullDeckFromDiscard(Player player, ActiveDeck deck) {
        if (!deck.isFullyCollectedInDiscard()) {
            return null;
        }
        ItemStack item = dismantle(player, deck, new ArrayList<>(deck.getDiscardPile()));
        playAtDiscardAnchor(deck, PluginSounds.PICK_UP_FULL_DECK);
        return item;
    }

    /**
     * Attempts to pick the full deck up straight from the main (dealing) pile, which is
     * only possible while every card of the deck is in it: the discard pile and the table
     * are empty. The current card order (e.g. the result of a shuffle) is preserved.
     *
     * @return the assembled deck item, or {@code null} if some card is elsewhere.
     */
    public ItemStack collectFullDeckFromMain(Player player, ActiveDeck deck) {
        if (!deck.isFullyCollectedInMain()) {
            return null;
        }
        ItemStack item = dismantle(player, deck, new ArrayList<>(deck.getMainPile()));
        playAtAnchor(deck, PluginSounds.PICK_UP_FULL_DECK);
        return item;
    }

    /**
     * Turns an active deck back into a deck item with the given card order: removes all of
     * its entities and forgets the deck.
     */
    private ItemStack dismantle(Player player, ActiveDeck deck, List<Card> order) {
        ItemStack item = DeckItemFactory.createFullDeckItem(deck.getDeckId(), deck.getType(), order, player);

        // Some of these ids may already be null (e.g. the main pile entities after the deck
        // had been fully dealt out, or the trump display for a deck type without one);
        // removing an already-absent entity id is a safe no-op either way.
        removeEntitySafely(deck.getMainDisplayEntityId());
        removeEntitySafely(deck.getDiscardDisplayEntityId());
        removeEntitySafely(deck.getTrumpDisplayEntityId());
        removeEntitySafely(deck.getMainInteractionEntityId());
        removeEntitySafely(deck.getDiscardInteractionEntityId());

        decks.remove(deck.getDeckId());
        markDirty();
        return item;
    }

    private void removeEntitySafely(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
    }

    // ------------------------------------------------------------------
    // Table (revealed / face-down cards)
    // ------------------------------------------------------------------

    /**
     * Puts {@code card} on the table face-up, starting a new stack, with a yaw equal to
     * {@code playerYaw}.
     */
    public RevealedCardInstance revealCard(ActiveDeck deck, Block block, Location clickPoint, Card card, float playerYaw) {
        return placeNewCard(deck, block, clickPoint, card, TransformUtil.normalizeYaw(playerYaw), false);
    }

    /**
     * Puts {@code card} on the table face-down (only the card back is shown), starting a
     * new stack, with a yaw equal to {@code playerYaw}.
     */
    public RevealedCardInstance placeCardFaceDown(ActiveDeck deck, Block block, Location clickPoint, Card card, float playerYaw) {
        return placeNewCard(deck, block, clickPoint, card, TransformUtil.normalizeYaw(playerYaw), true);
    }

    private RevealedCardInstance placeNewCard(ActiveDeck deck, Block block, Location clickPoint,
                                              Card card, float yaw, boolean faceDown) {
        double topY = block.getY() + 1.0 + STACK_LAYER_STEP;
        Location loc = new Location(block.getWorld(), clickPoint.getX(), topY, clickPoint.getZ());

        TextDisplay display = displayFactory.spawnRevealedCardDisplay(loc, yaw, deck.getDeckId(), card, faceDown);
        Interaction interaction = displayFactory.spawnRevealedCardInteraction(loc, deck.getDeckId(), card);
        RevealedCardInstance instance = new RevealedCardInstance(
                card, display.getUniqueId(), interaction.getUniqueId(), loc, yaw, UUID.randomUUID(), faceDown);
        deck.getRevealedCards().add(instance);
        markDirty();
        SoundUtil.play(loc, PluginSounds.PUT_CARD_ON_TABLE);
        return instance;
    }

    /**
     * Stacks {@code newCard} on top of {@code target}. Only ever called for the current
     * top of a stack (an entity click can only land on the top card's interaction hitbox
     * &mdash; see {@code EntityInteractListener}), so this reuses {@code target}'s
     * interaction entity for the new top card instead of spawning a fresh one for every
     * card added: it is relocated and re-tagged to the new card, while {@code target}
     * becomes purely visual (its interaction id is cleared, since it is now buried).
     * <p>
     * The new card always inherits the target's orientation (face-up onto face-up,
     * face-down onto face-down); how it is placed depends on {@code evenStack}:
     * <ul>
     *     <li>{@code false} (plain click): same X/Z, yaw rotated by a random 22.5-45&deg;
     *     relative to the target (a messy pile);</li>
     *     <li>{@code true} (shift + click): exactly the same yaw, shifted by
     *     {@link #STACK_POSITION_OFFSET} in the direction that yaw "looks" (a tidy,
     *     staggered pile).</li>
     * </ul>
     */
    public RevealedCardInstance stackCard(ActiveDeck deck, RevealedCardInstance target, Card newCard, boolean evenStack) {
        Location baseLoc = target.getLocation();
        boolean faceDown = target.isFaceDown();

        double newX = baseLoc.getX();
        double newZ = baseLoc.getZ();
        float newYaw;
        if (evenStack) {
            newYaw = target.getYaw();
            // Same convention as a player's look direction: yaw 0 = +Z, yaw 90 = -X.
            double radians = Math.toRadians(newYaw);
            newX -= Math.sin(radians) * STACK_POSITION_OFFSET;
            newZ += Math.cos(radians) * STACK_POSITION_OFFSET;
        } else {
            float increment = 22.5f + random.nextFloat() * (45f - 22.5f);
            newYaw = TransformUtil.normalizeYaw(target.getYaw() + increment);
        }
        Location newLoc = new Location(baseLoc.getWorld(), newX, baseLoc.getY() + STACK_LAYER_STEP, newZ);

        TextDisplay display = displayFactory.spawnRevealedCardDisplay(newLoc, newYaw, deck.getDeckId(), newCard, faceDown);
        Interaction interaction = reuseOrSpawnInteraction(target.getInteractionEntityUuid(), newLoc, deck.getDeckId(), newCard);

        RevealedCardInstance instance = new RevealedCardInstance(
                newCard, display.getUniqueId(), interaction.getUniqueId(), newLoc, newYaw,
                target.getStackId(), faceDown);
        target.setInteractionEntityUuid(null); // now buried; visual-only from here on
        deck.getRevealedCards().add(instance);
        markDirty();
        SoundUtil.play(newLoc, PluginSounds.PUT_CARD_ON_TABLE);
        return instance;
    }

    /**
     * Flips every card of the stack {@code anyCardOfStack} belongs to, in place: face-down
     * cards become face-up and face-up cards become face-down (positions, yaws and the
     * order of the stack are kept; only the displayed glyph changes).
     */
    public void flipStack(ActiveDeck deck, RevealedCardInstance anyCardOfStack) {
        boolean changed = false;
        for (RevealedCardInstance candidate : deck.getRevealedCards()) {
            if (!candidate.getStackId().equals(anyCardOfStack.getStackId())) {
                continue;
            }
            boolean nowFaceDown = !candidate.isFaceDown();
            candidate.setFaceDown(nowFaceDown);
            Entity entity = Bukkit.getEntity(candidate.getEntityUuid());
            if (entity instanceof TextDisplay textDisplay) {
                displayFactory.setRevealedCardFace(textDisplay, candidate.getCard(), nowFaceDown);
            }
            changed = true;
        }
        if (changed) {
            markDirty();
            SoundUtil.play(anyCardOfStack.getLocation(), PluginSounds.PUT_CARD_ON_TABLE);
        }
    }

    public void takeRevealedCard(ActiveDeck deck, RevealedCardInstance instance) {
        SoundUtil.play(instance.getLocation(), PluginSounds.TAKE_CARD_FROM_TABLE);
        removeEntitySafely(instance.getEntityUuid());

        UUID interactionUuid = instance.getInteractionEntityUuid();
        deck.getRevealedCards().remove(instance);

        if (interactionUuid != null) {
            // instance was the top of its stack (only the top ever carries a non-null
            // interaction id); if another card remains underneath in the same stack, hand
            // the same interaction entity down to it instead of destroying and re-spawning
            // one, since it is now the exposed top card.
            RevealedCardInstance newTop = findTopOfStack(deck, instance.getStackId());
            if (newTop != null) {
                Interaction reused = reuseOrSpawnInteraction(interactionUuid, newTop.getLocation(), deck.getDeckId(), newTop.getCard());
                newTop.setInteractionEntityUuid(reused.getUniqueId());
            } else {
                removeEntitySafely(interactionUuid);
            }
        }

        markDirty();
    }

    /**
     * Relocates and re-tags {@code existingInteractionUuid} (if it still refers to a live
     * {@link Interaction} entity) to represent {@code card} at {@code visualCenter},
     * avoiding a fresh spawn; otherwise spawns a new one as a fallback (e.g. if the
     * original entity was removed by some other means in the meantime).
     */
    private Interaction reuseOrSpawnInteraction(UUID existingInteractionUuid, Location visualCenter, UUID deckId, Card card) {
        if (existingInteractionUuid != null) {
            Entity existing = Bukkit.getEntity(existingInteractionUuid);
            if (existing instanceof Interaction existingInteraction) {
                displayFactory.relocateRevealedCardInteraction(existingInteraction, visualCenter, deckId, card);
                return existingInteraction;
            }
        }
        return displayFactory.spawnRevealedCardInteraction(visualCenter, deckId, card);
    }

    // ------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}