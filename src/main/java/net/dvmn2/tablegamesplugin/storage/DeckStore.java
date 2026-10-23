package net.dvmn2.tablegamesplugin.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.dvmn2.tablegamesplugin.model.ActiveDeck;
import net.dvmn2.tablegamesplugin.model.Anchor;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.DeckType;
import net.dvmn2.tablegamesplugin.model.RevealedCardInstance;
import net.dvmn2.tablegamesplugin.storage.dto.CardDto;
import net.dvmn2.tablegamesplugin.storage.dto.DeckDto;
import net.dvmn2.tablegamesplugin.storage.dto.RevealedCardDto;
import net.dvmn2.tablegamesplugin.storage.dto.StorageFileDto;
import net.dvmn2.tablegamesplugin.util.Uuids;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists the logical state of every active deck to {@code decks.json}, independent of entity
 * data, so state survives even if entities are lost.
 * <p>
 * Loading is tolerant: a damaged deck entry is skipped with a warning, and an unreadable file is
 * moved aside instead of being overwritten. {@link #buildSnapshot} must run on the main thread
 * because it reads live deck state; {@link #writeSnapshot} touches only the snapshot and the
 * file system and may run on any thread. Writes carry a sequence number so a slow older write can
 * never replace a newer one.
 */
public final class DeckStore {

    /** Version written to new files. */
    private static final int FORMAT_VERSION = 1;

    private static final int MAX_DECKS = 10_000;
    private static final int MAX_CARDS_PER_LIST = 64;
    private static final int MAX_REVEALED_PER_DECK = 64;

    private final Path file;
    private final Logger log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private long lastWrittenSequence;
    private boolean writesDisabled;
    private boolean writeFailureLogged;

    public DeckStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Reads every valid deck from disk. Thread: main server thread, at startup. */
    public List<ActiveDeck> loadAll() {
        if (!Files.exists(file)) {
            return List.of();
        }
        StorageFileDto root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = gson.fromJson(reader, StorageFileDto.class);
        } catch (JsonParseException ex) {
            log.log(Level.SEVERE, "Table games state is not valid JSON; moving it aside and starting empty", ex);
            quarantineFile();
            return List.of();
        } catch (IOException ex) {
            writesDisabled = true;
            log.log(Level.SEVERE, "Failed to read table games state; saving is disabled to protect "
                    + file.getFileName(), ex);
            return List.of();
        }
        if (root == null || root.decks == null) {
            return List.of();
        }
        if (root.version > FORMAT_VERSION) {
            log.warning(file.getFileName() + " has format version " + root.version
                    + ", newer than supported " + FORMAT_VERSION + "; unknown data is ignored");
        }
        if (root.decks.size() > MAX_DECKS) {
            log.warning(file.getFileName() + " lists " + root.decks.size() + " decks; only the first "
                    + MAX_DECKS + " are loaded");
        }
        List<ActiveDeck> result = new ArrayList<>();
        int limit = Math.min(root.decks.size(), MAX_DECKS);
        for (int i = 0; i < limit; i++) {
            DeckDto dto = root.decks.get(i);
            try {
                result.add(fromDto(dto));
            } catch (IllegalArgumentException ex) {
                log.warning("Skipping stored deck #" + i + " in " + file.getFileName() + ": " + ex.getMessage());
            }
        }
        return result;
    }

    /** Builds a plain-data copy of the decks. Cheap (no I/O); thread: main server thread. */
    public StorageFileDto buildSnapshot(Collection<ActiveDeck> decks) {
        StorageFileDto root = new StorageFileDto();
        root.version = FORMAT_VERSION;
        root.decks = new ArrayList<>(decks.size());
        for (ActiveDeck deck : decks) {
            root.decks.add(toDto(deck));
        }
        return root;
    }

    /**
     * Writes a snapshot atomically. Older snapshots than the last written one are dropped. Thread: any.
     *
     * @param sequence strictly increasing number assigned when the snapshot was taken
     */
    public synchronized void writeSnapshot(long sequence, StorageFileDto snapshot) {
        if (writesDisabled || sequence < lastWrittenSequence) {
            return;
        }
        try {
            writeAtomically(file, gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
            lastWrittenSequence = sequence;
            writeFailureLogged = false;
        } catch (IOException ex) {
            if (!writeFailureLogged) {
                writeFailureLogged = true;
                log.log(Level.SEVERE, "Failed to save table games state", ex);
            }
        }
    }

    private void quarantineFile() {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt");
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            writesDisabled = true;
            log.log(Level.SEVERE, "Could not move the damaged file aside; saving is disabled", ex);
        }
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---------- Domain -> DTO ----------

    private DeckDto toDto(ActiveDeck deck) {
        DeckDto dto = new DeckDto();
        dto.deckId = deck.getDeckId().toString();
        dto.deckType = deck.getType().name();
        dto.definition = toCardDtos(deck.getDefinition());
        dto.mainPile = toCardDtos(deck.getMainPile());
        dto.discardPile = toCardDtos(deck.getDiscardPile());
        dto.revealedCards = new ArrayList<>();
        for (RevealedCardInstance revealed : deck.getRevealedCards()) {
            dto.revealedCards.add(toDto(revealed, deck.getAnchor().world()));
        }
        Anchor anchor = deck.getAnchor();
        dto.world = anchor.world();
        dto.anchorBlockX = anchor.x();
        dto.anchorBlockY = anchor.y();
        dto.anchorBlockZ = anchor.z();
        dto.mainDisplayEntityId = idToString(deck.getMainDisplayId());
        dto.discardDisplayEntityId = idToString(deck.getDiscardDisplayId());
        dto.trumpDisplayEntityId = idToString(deck.getTrumpDisplayId());
        dto.mainInteractionEntityId = idToString(deck.getMainInteractionId());
        dto.discardInteractionEntityId = idToString(deck.getDiscardInteractionId());
        return dto;
    }

    private RevealedCardDto toDto(RevealedCardInstance revealed, String fallbackWorld) {
        RevealedCardDto dto = new RevealedCardDto();
        Card card = revealed.getCard();
        dto.cardId = card.cardId().toString();
        dto.symbol = card.symbol();
        dto.entityUuid = revealed.getEntityUuid().toString();
        dto.interactionEntityUuid = idToString(revealed.getInteractionEntityUuid());
        dto.stackId = revealed.getStackId().toString();
        dto.faceDown = revealed.isFaceDown();
        Location location = revealed.getLocation();
        World world = location.getWorld();
        dto.world = world != null ? world.getName() : fallbackWorld;
        dto.x = location.getX();
        dto.y = location.getY();
        dto.z = location.getZ();
        dto.yaw = revealed.getYaw();
        return dto;
    }

    private List<CardDto> toCardDtos(List<Card> cards) {
        List<CardDto> list = new ArrayList<>(cards.size());
        for (Card card : cards) {
            CardDto dto = new CardDto();
            dto.cardId = card.cardId().toString();
            dto.symbol = card.symbol();
            list.add(dto);
        }
        return list;
    }

    @Nullable
    private static String idToString(@Nullable UUID id) {
        return id == null ? null : id.toString();
    }

    // ---------- DTO -> domain (throws IllegalArgumentException on damaged data) ----------

    private ActiveDeck fromDto(@Nullable DeckDto dto) {
        require(dto != null, "entry is null");
        UUID deckId = requireUuid(dto.deckId, "deckId");
        DeckType type = DeckType.fromName(dto.deckType);
        require(type != null, "unknown deckType '" + dto.deckType + "'");
        require(dto.world != null && !dto.world.isBlank(), "world is missing");

        List<Card> definition = readCards(dto.definition, deckId, type, "definition");
        require(definition.size() == type.getDeckSize(),
                "definition has " + definition.size() + " cards, expected " + type.getDeckSize());

        Anchor anchor = new Anchor(dto.world, dto.anchorBlockX, dto.anchorBlockY, dto.anchorBlockZ);
        ActiveDeck deck = new ActiveDeck(deckId, type, definition, anchor);
        for (Card card : readCards(dto.mainPile, deckId, type, "mainPile")) {
            deck.pushMain(card);
        }
        for (Card card : readCards(dto.discardPile, deckId, type, "discardPile")) {
            deck.pushDiscard(card);
        }
        deck.setMainDisplayId(Uuids.parseOrNull(dto.mainDisplayEntityId));
        deck.setDiscardDisplayId(Uuids.parseOrNull(dto.discardDisplayEntityId));
        deck.setTrumpDisplayId(Uuids.parseOrNull(dto.trumpDisplayEntityId));
        deck.setMainInteractionId(Uuids.parseOrNull(dto.mainInteractionEntityId));
        deck.setDiscardInteractionId(Uuids.parseOrNull(dto.discardInteractionEntityId));

        List<RevealedCardDto> revealed = dto.revealedCards == null ? List.of() : dto.revealedCards;
        require(revealed.size() <= MAX_REVEALED_PER_DECK, "revealedCards has " + revealed.size() + " entries");
        for (RevealedCardDto revealedDto : revealed) {
            deck.addRevealed(readRevealed(revealedDto, deckId, type));
        }
        return deck;
    }

    private RevealedCardInstance readRevealed(@Nullable RevealedCardDto dto, UUID deckId, DeckType type) {
        require(dto != null, "revealed card entry is null");
        Card card = readCard(dto.cardId, dto.symbol, deckId, type, "revealedCards");
        UUID entityId = requireUuid(dto.entityUuid, "revealedCards.entityUuid");
        UUID stackId = requireUuid(dto.stackId, "revealedCards.stackId");
        require(Double.isFinite(dto.x) && Double.isFinite(dto.y) && Double.isFinite(dto.z)
                && Float.isFinite(dto.yaw), "revealedCards has a non-finite position");
        World world = dto.world == null ? null : Bukkit.getWorld(dto.world);
        Location location = new Location(world, dto.x, dto.y, dto.z);
        return new RevealedCardInstance(card, entityId, Uuids.parseOrNull(dto.interactionEntityUuid),
                location, dto.yaw, stackId, dto.faceDown);
    }

    private List<Card> readCards(@Nullable List<CardDto> dtos, UUID deckId, DeckType type, String field) {
        if (dtos == null) {
            return List.of();
        }
        require(dtos.size() <= MAX_CARDS_PER_LIST, field + " has " + dtos.size() + " cards");
        List<Card> cards = new ArrayList<>(dtos.size());
        for (CardDto dto : dtos) {
            require(dto != null, field + " contains a null card");
            cards.add(readCard(dto.cardId, dto.symbol, deckId, type, field));
        }
        return cards;
    }

    private Card readCard(@Nullable String cardId, @Nullable String symbol, UUID deckId, DeckType type,
                          String field) {
        UUID id = requireUuid(cardId, field + ".cardId");
        require(type.isValidSymbol(symbol), field + " has an invalid symbol for " + type);
        return new Card(id, deckId, symbol);
    }

    private static UUID requireUuid(@Nullable String raw, String field) {
        UUID id = Uuids.parseOrNull(raw);
        require(id != null, field + " is not a valid UUID");
        return id;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
