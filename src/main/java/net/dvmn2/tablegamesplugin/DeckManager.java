package net.dvmn2.tablegamesplugin;

import net.dvmn2.tablegamesplugin.model.ActiveDeck;
import net.dvmn2.tablegamesplugin.model.Anchor;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.DeckItemData;
import net.dvmn2.tablegamesplugin.model.EntityRole;
import net.dvmn2.tablegamesplugin.model.RevealedCardInstance;
import net.dvmn2.tablegamesplugin.storage.DeckStore;
import net.dvmn2.tablegamesplugin.storage.dto.StorageFileDto;
import net.dvmn2.tablegamesplugin.util.PlayerItems;
import net.dvmn2.tablegamesplugin.util.PluginSounds;
import net.dvmn2.tablegamesplugin.util.TransformUtil;
import net.dvmn2.tablegamesplugin.util.Uuids;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Owns every active physical deck: creation, shuffling, dealing, discarding, putting cards on the
 * table and reassembling a full deck. Delegates entity creation to {@link DisplayFactory} and
 * persistence to {@link DeckStore}.
 * <p>
 * Decks and their entities are persistent by design: they stay in the world across restarts, so
 * {@link #shutdown()} saves the state but does not remove entities. Entities that carry a plugin
 * tag but belong to no known deck are removed as orphans.
 * <p>
 * Operations with several outcomes return an enum; the caller decides what to tell the player.
 * Thread: main server thread only.
 */
public final class DeckManager {

    /** Outcome of putting a deck on a block. */
    public enum PlaceResult { SUCCESS, ALREADY_ACTIVE, NO_CLEAR_SURFACE }

    /** Outcome of taking a card from a pile. */
    public enum TakeResult { SUCCESS, EMPTY }

    /** Outcome of putting a card somewhere. */
    public enum MoveResult { SUCCESS, WRONG_DECK }

    /** Outcome of reassembling the full deck item. */
    public enum CollectResult { SUCCESS, NOT_COLLECTED }

    /** Vertical distance between two cards of one table stack, blocks; avoids z-fighting. */
    private static final double STACK_LAYER_STEP = 0.001;

    /** Shift of an evenly stacked card along the direction the lower card faces, blocks. */
    private static final double STACK_POSITION_OFFSET = 0.1;

    /** Random yaw step of a messily stacked card, degrees. */
    private static final float MIN_STACK_YAW_STEP_DEGREES = 22.5f;
    private static final float MAX_STACK_YAW_STEP_DEGREES = 45f;

    private static final float FULL_TURN_DEGREES = 360f;

    /** The trump text is turned a quarter turn relative to the pile so it reads sideways under the top card. */
    private static final float TRUMP_YAW_OFFSET_DEGREES = 90f;

    /** The trump text floats this far above the pile surface, blocks, to avoid z-fighting. */
    private static final double TRUMP_LIFT = 0.01;

    private static final long TICKS_PER_SECOND = 20L;

    private final Plugin plugin;
    private final Supplier<TableGamesSettings> settings;
    private final PluginKeys keys;
    private final DisplayFactory displays;
    private final DeckItemFactory deckItems;
    private final CardItemFactory cardItems;
    private final SoundPlayer sounds;
    private final DeckStore store;
    private final Random random = new Random();
    private final Map<UUID, ActiveDeck> decks = new HashMap<>();

    private @Nullable BukkitTask flushTask;
    private boolean started;
    private boolean dirty;
    private long snapshotSequence;

    public DeckManager(Plugin plugin, Supplier<TableGamesSettings> settings, PluginKeys keys,
                       DisplayFactory displays, DeckItemFactory deckItems, CardItemFactory cardItems,
                       SoundPlayer sounds, DeckStore store) {
        this.plugin = plugin;
        this.settings = settings;
        this.keys = keys;
        this.displays = displays;
        this.deckItems = deckItems;
        this.cardItems = cardItems;
        this.sounds = sounds;
        this.store = store;
    }

    // ---------- Lifecycle ----------

    /** Loads saved decks, removes orphaned entities and starts the periodic save task. */
    public void start() {
        for (ActiveDeck deck : store.loadAll()) {
            decks.put(deck.getDeckId(), deck);
            relinkEntities(deck);
        }
        started = true;
        removeOrphansInLoadedWorlds();
        scheduleFlush();
    }

    /** Stops the periodic task and saves the state synchronously. */
    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (started) {
            store.writeSnapshot(++snapshotSequence, store.buildSnapshot(decks.values()));
            dirty = false;
        }
    }

    /** Applies a changed save interval. */
    public void onSettingsReloaded() {
        scheduleFlush();
    }

    public int getDeckCount() {
        return decks.size();
    }

    @Nullable
    public ActiveDeck getDeck(UUID deckId) {
        return decks.get(deckId);
    }

    /**
     * The deck with this id, but only if its main pile is within the configured reveal radius of
     * {@code point}. A card can be put on the table only near its own deck; it must never be adopted
     * by another deck that merely stands nearby.
     */
    @Nullable
    public ActiveDeck getDeckWithinRange(UUID deckId, Location point) {
        ActiveDeck deck = decks.get(deckId);
        if (deck == null) {
            return null;
        }
        Location center = deck.getAnchor().mainCenter();
        if (center == null || !center.getWorld().equals(point.getWorld())) {
            return null;
        }
        double radius = settings.get().revealRadiusBlocks();
        return center.distanceSquared(point) <= radius * radius ? deck : null;
    }

    // ---------- Deck item and physical deck ----------

    /** Returns a new deck item with the cards of {@code data} in random order and plays the shuffle sound. */
    public ItemStack shuffleDeckItem(Player player, DeckItemData data) {
        List<Card> shuffled = new ArrayList<>(data.cards());
        Collections.shuffle(shuffled, random);
        sounds.play(player.getLocation(), PluginSounds.SHUFFLE_DECK);
        return deckItems.createFullDeckItem(data.deckId(), data.type(), shuffled, player);
    }

    /**
     * Materialises a deck item as a physical deck on top of {@code clickedBlock}. On any result other
     * than {@link PlaceResult#SUCCESS} nothing changes and the item must be left untouched.
     */
    public PlaceResult createPhysicalDeck(Block clickedBlock, DeckItemData data) {
        if (decks.containsKey(data.deckId())) {
            return PlaceResult.ALREADY_ACTIVE;
        }
        // The discard pile always sits one block to the east, regardless of player facing, so both
        // blocks need a solid top with clear space above.
        Block discardBlock = clickedBlock.getRelative(BlockFace.EAST);
        if (!hasClearSolidSurface(clickedBlock) || !hasClearSolidSurface(discardBlock)) {
            return PlaceResult.NO_CLEAR_SURFACE;
        }

        Anchor anchor = new Anchor(clickedBlock.getWorld().getName(),
                clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ());
        Location mainLocation = clickedBlock.getLocation().add(0.5, 1.0, 0.5);
        Location discardLocation = clickedBlock.getLocation().add(1.5, 1.0, 0.5);
        float mainYaw = TransformUtil.normalizeYaw(random.nextFloat() * FULL_TURN_DEGREES);
        float discardYaw = TransformUtil.normalizeYaw(random.nextFloat() * FULL_TURN_DEGREES);

        ActiveDeck deck = new ActiveDeck(data.deckId(), data.type(), data.cards(), anchor);
        for (Card card : data.cards()) {
            deck.pushMain(card);
        }
        deck.setMainDisplayId(displays.spawnPileDisplay(mainLocation, mainYaw, data.deckId(), EntityRole.DECK)
                .getUniqueId());
        deck.setDiscardDisplayId(displays.spawnPileDisplay(discardLocation, discardYaw, data.deckId(),
                EntityRole.DISCARD).getUniqueId());
        deck.setMainInteractionId(displays.spawnPileInteraction(mainLocation, data.deckId(), EntityRole.DECK)
                .getUniqueId());
        deck.setDiscardInteractionId(displays.spawnPileInteraction(discardLocation, data.deckId(),
                EntityRole.DISCARD).getUniqueId());

        // Only a deck type with a trump shows its bottom card; showing it for poker would reveal a card
        // that must stay hidden.
        Card trump = deck.getTrumpCard();
        if (trump != null) {
            Location trumpLocation = mainLocation.clone().add(0, TRUMP_LIFT, 0);
            deck.setTrumpDisplayId(displays.spawnTrumpDisplay(trumpLocation, mainYaw + TRUMP_YAW_OFFSET_DEGREES,
                    data.deckId(), trump).getUniqueId());
        }

        decks.put(deck.getDeckId(), deck);
        markDirty();
        sounds.play(mainLocation, PluginSounds.PLACE_DECK);
        return PlaceResult.SUCCESS;
    }

    // ---------- Main pile ----------

    /** Gives the top card of the main pile to the player. */
    public TakeResult giveTopCard(Player player, ActiveDeck deck) {
        Card top = deck.popMain();
        if (top == null) {
            return TakeResult.EMPTY;
        }
        PlayerItems.giveOrDrop(player, cardItems.createCardItem(top));
        sounds.play(deck.getAnchor().mainCenter(), PluginSounds.TAKE_CARD_FROM_PILE);

        if (deck.getMainPile().isEmpty()) {
            // Nothing left to show or click: remove the trump text, the pile display and its hitbox
            // together instead of leaving two meaningless entities behind.
            removeMainPileEntities(deck);
        } else {
            updateTrumpDisplay(deck);
        }
        markDirty();
        return TakeResult.SUCCESS;
    }

    /**
     * Inserts a card into the main pile at a random inner position: never at the very bottom (that is
     * the trump) and at the very top only when at most one card remains.
     */
    public MoveResult returnCardToDeck(ActiveDeck deck, Card card) {
        if (!card.deckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        int size = deck.getMainPile().size();
        int insertIndex = size <= 1 ? size : 1 + random.nextInt(size - 1);
        deck.insertMain(insertIndex, card);
        afterMainPileInsert(deck);
        return MoveResult.SUCCESS;
    }

    /** Puts a card on top of the main pile, the next one to be dealt. */
    public MoveResult putCardOnTopOfDeck(ActiveDeck deck, Card card) {
        if (!card.deckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        deck.pushMain(card);
        afterMainPileInsert(deck);
        return MoveResult.SUCCESS;
    }

    // ---------- Discard pile ----------

    /** Puts a card on top of the discard pile. */
    public MoveResult addCardToDiscard(ActiveDeck deck, Card card) {
        if (!card.deckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        deck.pushDiscard(card);
        afterDiscardChange(deck, PluginSounds.PUT_CARD_INTO_PILE);
        return MoveResult.SUCCESS;
    }

    /** Inserts a card at a random position of the discard pile, bottom and top included. */
    public MoveResult insertCardIntoDiscardRandomly(ActiveDeck deck, Card card) {
        if (!card.deckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        deck.insertDiscard(random.nextInt(deck.getDiscardPile().size() + 1), card);
        afterDiscardChange(deck, PluginSounds.PUT_CARD_INTO_PILE);
        return MoveResult.SUCCESS;
    }

    /** Gives the top card of the discard pile to the player. */
    public TakeResult takeTopFromDiscard(Player player, ActiveDeck deck) {
        Card top = deck.popDiscard();
        if (top == null) {
            return TakeResult.EMPTY;
        }
        PlayerItems.giveOrDrop(player, cardItems.createCardItem(top));
        afterDiscardChange(deck, PluginSounds.TAKE_CARD_FROM_PILE);
        return TakeResult.SUCCESS;
    }

    // ---------- Reassembling the full deck ----------

    /** Reassembles the deck item from the discard pile; possible only when every card is in it. */
    public CollectResult collectFullDeckFromDiscard(Player player, ActiveDeck deck) {
        if (!deck.isFullyCollectedInDiscard()) {
            return CollectResult.NOT_COLLECTED;
        }
        sounds.play(deck.getAnchor().discardCenter(), PluginSounds.PICK_UP_FULL_DECK);
        dismantle(player, deck, new ArrayList<>(deck.getDiscardPile()));
        return CollectResult.SUCCESS;
    }

    /**
     * Reassembles the deck item straight from the main pile; possible only when every card is in it.
     * The current card order, for example the result of a shuffle, is preserved.
     */
    public CollectResult collectFullDeckFromMain(Player player, ActiveDeck deck) {
        if (!deck.isFullyCollectedInMain()) {
            return CollectResult.NOT_COLLECTED;
        }
        sounds.play(deck.getAnchor().mainCenter(), PluginSounds.PICK_UP_FULL_DECK);
        dismantle(player, deck, new ArrayList<>(deck.getMainPile()));
        return CollectResult.SUCCESS;
    }

    // ---------- Table ----------

    /** Puts {@code card} on the table face up as a new stack, looking the way the player looks. */
    public void revealCard(ActiveDeck deck, Block block, Location clickPoint, Card card, float playerYaw) {
        placeNewCard(deck, block, clickPoint, card, TransformUtil.normalizeYaw(playerYaw), false);
    }

    /** Puts {@code card} on the table face down as a new stack, looking the way the player looks. */
    public void placeCardFaceDown(ActiveDeck deck, Block block, Location clickPoint, Card card, float playerYaw) {
        placeNewCard(deck, block, clickPoint, card, TransformUtil.normalizeYaw(playerYaw), true);
    }

    /**
     * Stacks {@code newCard} on top of {@code target}, which must be the current top of its stack: a
     * click can only land on the top card's hitbox. That hitbox is reused for the new top card and the
     * target becomes visual only. The new card keeps the stack's orientation (face up or face down).
     * <ul>
     *     <li>{@code evenStack = false}: same X/Z, yaw rotated by a random 22.5 to 45 degrees (a messy pile)</li>
     *     <li>{@code evenStack = true}: same yaw, shifted by {@link #STACK_POSITION_OFFSET} along that yaw
     *     (a tidy, staggered pile)</li>
     * </ul>
     */
    public MoveResult stackCard(ActiveDeck deck, RevealedCardInstance target, Card newCard, boolean evenStack) {
        if (!newCard.deckId().equals(deck.getDeckId())) {
            return MoveResult.WRONG_DECK;
        }
        Location baseLocation = target.getLocation();
        boolean faceDown = target.isFaceDown();

        double newX = baseLocation.getX();
        double newZ = baseLocation.getZ();
        float newYaw;
        if (evenStack) {
            newYaw = target.getYaw();
            // Same convention as a player's look direction: yaw 0 = +Z, yaw 90 = -X.
            double radians = Math.toRadians(newYaw);
            newX -= Math.sin(radians) * STACK_POSITION_OFFSET;
            newZ += Math.cos(radians) * STACK_POSITION_OFFSET;
        } else {
            float step = MIN_STACK_YAW_STEP_DEGREES
                    + random.nextFloat() * (MAX_STACK_YAW_STEP_DEGREES - MIN_STACK_YAW_STEP_DEGREES);
            newYaw = TransformUtil.normalizeYaw(target.getYaw() + step);
        }
        Location newLocation = new Location(baseLocation.getWorld(), newX,
                baseLocation.getY() + STACK_LAYER_STEP, newZ);

        TextDisplay display = displays.spawnRevealedCardDisplay(newLocation, newYaw, deck.getDeckId(), newCard,
                faceDown);
        Interaction interaction = reuseOrSpawnInteraction(target.getInteractionEntityUuid(), newLocation,
                deck.getDeckId(), newCard);

        target.setInteractionEntityUuid(null);
        deck.addRevealed(new RevealedCardInstance(newCard, display.getUniqueId(), interaction.getUniqueId(),
                newLocation, newYaw, target.getStackId(), faceDown));
        markDirty();
        sounds.play(newLocation, PluginSounds.PUT_CARD_ON_TABLE);
        return MoveResult.SUCCESS;
    }

    /**
     * Flips every card of the stack {@code anyCardOfStack} belongs to: face-up cards become face down
     * and the other way round. Positions, yaws and order stay; only the displayed glyph changes.
     */
    public void flipStack(ActiveDeck deck, RevealedCardInstance anyCardOfStack) {
        boolean changed = false;
        for (RevealedCardInstance candidate : deck.getRevealedCards()) {
            if (!candidate.getStackId().equals(anyCardOfStack.getStackId())) {
                continue;
            }
            boolean nowFaceDown = !candidate.isFaceDown();
            candidate.setFaceDown(nowFaceDown);
            if (plugin.getServer().getEntity(candidate.getEntityUuid()) instanceof TextDisplay textDisplay) {
                displays.setRevealedCardFace(textDisplay, candidate.getCard(), nowFaceDown);
            }
            changed = true;
        }
        if (changed) {
            markDirty();
            sounds.play(anyCardOfStack.getLocation(), PluginSounds.PUT_CARD_ON_TABLE);
        }
    }

    /** Takes the top card of a table stack into the player's inventory. */
    public void takeRevealedCard(Player player, ActiveDeck deck, RevealedCardInstance instance) {
        sounds.play(instance.getLocation(), PluginSounds.TAKE_CARD_FROM_TABLE);
        removeEntitySafely(instance.getEntityUuid());

        UUID interactionId = instance.getInteractionEntityUuid();
        deck.removeRevealed(instance);

        if (interactionId != null) {
            // The instance was the top of its stack. If a card remains below, hand the same hitbox down
            // to it instead of destroying and respawning one.
            RevealedCardInstance newTop = findTopOfStack(deck, instance.getStackId());
            if (newTop != null) {
                Interaction reused = reuseOrSpawnInteraction(interactionId, newTop.getLocation(), deck.getDeckId(),
                        newTop.getCard());
                newTop.setInteractionEntityUuid(reused.getUniqueId());
            } else {
                removeEntitySafely(interactionId);
            }
        }
        PlayerItems.giveOrDrop(player, cardItems.createCardItem(instance.getCard()));
        markDirty();
    }

    // ---------- Orphaned entities ----------

    /**
     * Schedules removal, on the next tick, of those {@code entities} that carry a plugin tag but belong
     * to no known deck. Removal is deferred so the check sees decks registered later in the same tick.
     */
    public void removeOrphansLater(List<Entity> entities) {
        List<Entity> marked = new ArrayList<>();
        for (Entity entity : entities) {
            if (isMarked(entity)) {
                marked.add(entity);
            }
        }
        if (marked.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int removed = 0;
            for (Entity entity : marked) {
                if (entity.isValid() && isOrphan(entity)) {
                    entity.remove();
                    removed++;
                }
            }
            logRemovedOrphans(removed);
        });
    }

    private void removeOrphansInLoadedWorlds() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isMarked(entity) && isOrphan(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        logRemovedOrphans(removed);
    }

    private void logRemovedOrphans(int removed) {
        if (removed > 0) {
            plugin.getLogger().warning("Removed " + removed + " orphaned table game entities.");
        }
    }

    private boolean isMarked(Entity entity) {
        String role = entity.getPersistentDataContainer().get(keys.type(), PersistentDataType.STRING);
        return EntityRole.fromId(role) != null;
    }

    private boolean isOrphan(Entity entity) {
        String rawDeckId = entity.getPersistentDataContainer().get(keys.deckId(), PersistentDataType.STRING);
        UUID deckId = Uuids.parseOrNull(rawDeckId);
        ActiveDeck deck = deckId == null ? null : decks.get(deckId);
        return deck == null || !deck.referencesEntity(entity.getUniqueId());
    }

    // ---------- Internals ----------

    private void scheduleFlush() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        long period = settings.get().saveIntervalSeconds() * TICKS_PER_SECOND;
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushIfDirty, period, period);
    }

    /**
     * Takes a snapshot on the main thread and writes it asynchronously, so bursts of card actions never
     * block the server on file I/O. A hard crash can lose up to one save interval of changes.
     */
    private void flushIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        long sequence = ++snapshotSequence;
        StorageFileDto snapshot = store.buildSnapshot(decks.values());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> store.writeSnapshot(sequence, snapshot));
    }

    private void markDirty() {
        dirty = true;
    }

    private void relinkEntities(ActiveDeck deck) {
        Anchor anchor = deck.getAnchor();
        World world = plugin.getServer().getWorld(anchor.world());
        if (world == null) {
            plugin.getLogger().warning("World '" + anchor.world() + "' of deck " + deck.getDeckId()
                    + " is not loaded; its entities could not be verified.");
            return;
        }
        world.getChunkAt(anchor.x() >> 4, anchor.z() >> 4).load();
        world.getChunkAt((anchor.x() + 1) >> 4, anchor.z() >> 4).load();

        // Main pile ids are null once the pile was dealt out, and the trump id is null for decks without
        // a trump; verifyEntity treats null as expected.
        verifyEntity(deck, deck.getMainDisplayId(), ItemDisplay.class);
        verifyEntity(deck, deck.getDiscardDisplayId(), ItemDisplay.class);
        verifyEntity(deck, deck.getTrumpDisplayId(), TextDisplay.class);
        verifyEntity(deck, deck.getMainInteractionId(), Interaction.class);
        verifyEntity(deck, deck.getDiscardInteractionId(), Interaction.class);

        for (RevealedCardInstance revealed : deck.getRevealedCards()) {
            Location location = revealed.getLocation();
            if (location.getWorld() != null) {
                location.getWorld().getChunkAt(location).load();
            }
            verifyEntity(deck, revealed.getEntityUuid(), TextDisplay.class);
            // Only the top card of a stack has a hitbox, so a null id for a buried card is expected.
            verifyEntity(deck, revealed.getInteractionEntityUuid(), Interaction.class);
        }
    }

    private void verifyEntity(ActiveDeck deck, @Nullable UUID uuid, Class<? extends Entity> expectedType) {
        if (uuid == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(uuid);
        if (entity == null || !expectedType.isInstance(entity)) {
            // Lost visuals are never recreated automatically: the deck would silently change appearance.
            plugin.getLogger().warning("Expected " + expectedType.getSimpleName() + " " + uuid + " of deck "
                    + deck.getDeckId() + " was not found; it will not be recreated.");
        }
    }

    /** A block a pile display can sit on: solid, with passable space above it. */
    private boolean hasClearSolidSurface(Block block) {
        return block.getType().isSolid() && block.getRelative(BlockFace.UP).isPassable();
    }

    private void afterMainPileInsert(ActiveDeck deck) {
        updateTrumpDisplay(deck);
        markDirty();
        sounds.play(deck.getAnchor().mainCenter(), PluginSounds.PUT_CARD_INTO_PILE);
    }

    private void afterDiscardChange(ActiveDeck deck, String soundKey) {
        markDirty();
        sounds.play(deck.getAnchor().discardCenter(), soundKey);
    }

    private void removeMainPileEntities(ActiveDeck deck) {
        removeEntitySafely(deck.getTrumpDisplayId());
        removeEntitySafely(deck.getMainDisplayId());
        removeEntitySafely(deck.getMainInteractionId());
        deck.setTrumpDisplayId(null);
        deck.setMainDisplayId(null);
        deck.setMainInteractionId(null);
    }

    private void updateTrumpDisplay(ActiveDeck deck) {
        UUID trumpId = deck.getTrumpDisplayId();
        if (trumpId == null) {
            return; // deck type without a trump, or the main pile was already dealt out
        }
        if (plugin.getServer().getEntity(trumpId) instanceof TextDisplay textDisplay) {
            displays.setTrumpSymbol(textDisplay, deck.getTrumpCard());
        }
    }

    /** Turns an active deck back into a deck item with the given order and forgets the deck. */
    private void dismantle(Player player, ActiveDeck deck, List<Card> order) {
        ItemStack item = deckItems.createFullDeckItem(deck.getDeckId(), deck.getType(), order, player);

        // Some ids may already be null (main pile entities after a full deal-out, trump for decks without
        // one); removing an absent entity is a no-op.
        removeEntitySafely(deck.getMainDisplayId());
        removeEntitySafely(deck.getDiscardDisplayId());
        removeEntitySafely(deck.getTrumpDisplayId());
        removeEntitySafely(deck.getMainInteractionId());
        removeEntitySafely(deck.getDiscardInteractionId());

        decks.remove(deck.getDeckId());
        markDirty();
        PlayerItems.giveOrDrop(player, item);
    }

    private void removeEntitySafely(@Nullable UUID uuid) {
        if (uuid == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
    }

    private void placeNewCard(ActiveDeck deck, Block block, Location clickPoint, Card card, float yaw,
                              boolean faceDown) {
        double topY = block.getY() + 1.0 + STACK_LAYER_STEP;
        Location location = new Location(block.getWorld(), clickPoint.getX(), topY, clickPoint.getZ());

        TextDisplay display = displays.spawnRevealedCardDisplay(location, yaw, deck.getDeckId(), card, faceDown);
        Interaction interaction = displays.spawnRevealedCardInteraction(location, deck.getDeckId(), card);
        deck.addRevealed(new RevealedCardInstance(card, display.getUniqueId(), interaction.getUniqueId(),
                location, yaw, UUID.randomUUID(), faceDown));
        markDirty();
        sounds.play(location, PluginSounds.PUT_CARD_ON_TABLE);
    }

    /**
     * The top-most card of a stack (highest Y; a later list position wins a tie), or {@code null} if the
     * stack is empty. Stacks are identified by id, not position, because face-down stacks are shifted.
     */
    @Nullable
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

    /**
     * Relocates and re-tags the hitbox {@code existingId} (if it is still a live {@link Interaction}) for
     * {@code card}; otherwise spawns a new one, for example after the original was removed by other means.
     */
    private Interaction reuseOrSpawnInteraction(@Nullable UUID existingId, Location visualCenter, UUID deckId,
                                                Card card) {
        if (existingId != null
                && plugin.getServer().getEntity(existingId) instanceof Interaction existing) {
            displays.relocateRevealedCardInteraction(existing, visualCenter, deckId, card);
            return existing;
        }
        return displays.spawnRevealedCardInteraction(visualCenter, deckId, card);
    }
}
