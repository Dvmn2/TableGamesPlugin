package net.dvmn2.tablegamesplugin.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dvmn2.tablegamesplugin.TableGamesPlugin;
import net.dvmn2.tablegamesplugin.model.ActiveDeck;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.DeckType;
import net.dvmn2.tablegamesplugin.model.RevealedCardInstance;
import net.dvmn2.tablegamesplugin.storage.dto.CardDto;
import net.dvmn2.tablegamesplugin.storage.dto.DeckDto;
import net.dvmn2.tablegamesplugin.storage.dto.RevealedCardDto;
import net.dvmn2.tablegamesplugin.storage.dto.StorageFileDto;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Persists the full logical state of every active deck to a simple JSON file
 * ({@code decks.json} in the plugin's data folder), independent of any entity PDC data,
 * so that state survives even if entities are lost (specification section 26).
 * <p>
 * {@link #buildSnapshot(Collection)} and {@link #writeSnapshot(StorageFileDto)} are split
 * so that callers (see {@code DeckManager#flushIfDirty()}) can take a cheap, synchronous,
 * in-memory copy of the current state on the main thread, then perform the slow disk
 * write on a separate thread without racing the live {@link ActiveDeck} collections.
 * {@link #saveAll(Collection)} remains available for callers that want an immediate,
 * synchronous, do-both-steps save (e.g. on plugin disable).
 */
public final class StorageManager {

    private final TableGamesPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File storageFile;

    public StorageManager(TableGamesPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "decks.json");
    }

    public synchronized List<ActiveDeck> loadAll() {
        List<ActiveDeck> result = new ArrayList<>();
        if (!storageFile.exists()) {
            return result;
        }
        try (Reader reader = Files.newBufferedReader(storageFile.toPath(), StandardCharsets.UTF_8)) {
            StorageFileDto fileDto = gson.fromJson(reader, StorageFileDto.class);
            if (fileDto == null || fileDto.decks == null) {
                return result;
            }
            for (DeckDto deckDto : fileDto.decks) {
                ActiveDeck deck = fromDto(deckDto);
                if (deck != null) {
                    result.add(deck);
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to load playing cards state: " + ex.getMessage());
        }
        return result;
    }

    /**
     * Builds a plain-data snapshot of the given decks. Cheap (no I/O) and must be called
     * on the same thread that mutates {@link ActiveDeck}'s internal lists (the main
     * thread), since it reads them directly.
     */
    public StorageFileDto buildSnapshot(Collection<ActiveDeck> decks) {
        StorageFileDto fileDto = new StorageFileDto();
        fileDto.decks = new ArrayList<>();
        for (ActiveDeck deck : decks) {
            fileDto.decks.add(toDto(deck));
        }
        return fileDto;
    }

    /**
     * Writes an already-built snapshot to disk. Safe to call from any thread (including
     * asynchronously) since it only touches the immutable DTO tree and the filesystem.
     */
    public synchronized void writeSnapshot(StorageFileDto fileDto) {
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(parent, storageFile.getName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(fileDto, writer);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to write playing cards state: " + ex.getMessage());
            return;
        }

        try {
            Files.move(tempFile.toPath(), storageFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            try {
                Files.move(tempFile.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackEx) {
                plugin.getLogger().severe("Failed to persist playing cards state: " + fallbackEx.getMessage());
            }
        }
    }

    /**
     * Convenience: builds and immediately writes a snapshot, synchronously, on this thread.
     */
    public void saveAll(Collection<ActiveDeck> decks) {
        writeSnapshot(buildSnapshot(decks));
    }

    private DeckDto toDto(ActiveDeck deck) {
        DeckDto dto = new DeckDto();
        dto.deckId = deck.getDeckId().toString();
        dto.deckType = deck.getType().name();
        dto.definition = toCardDtoList(deck.getDefinition());
        dto.mainPile = toCardDtoList(deck.getMainPile());
        dto.discardPile = toCardDtoList(deck.getDiscardPile());

        dto.revealedCards = new ArrayList<>();
        for (RevealedCardInstance revealed : deck.getRevealedCards()) {
            RevealedCardDto revealedDto = new RevealedCardDto();
            Card card = revealed.getCard();
            revealedDto.cardId = card.getCardId().toString();
            revealedDto.symbol = card.getSymbol();
            revealedDto.entityUuid = uuidToString(revealed.getEntityUuid());
            revealedDto.interactionEntityUuid = uuidToString(revealed.getInteractionEntityUuid());
            revealedDto.stackId = uuidToString(revealed.getStackId());
            revealedDto.faceDown = revealed.isFaceDown();
            Location loc = revealed.getLocation();
            revealedDto.world = loc.getWorld() != null ? loc.getWorld().getName() : deck.getWorldName();
            revealedDto.x = loc.getX();
            revealedDto.y = loc.getY();
            revealedDto.z = loc.getZ();
            revealedDto.yaw = revealed.getYaw();
            dto.revealedCards.add(revealedDto);
        }

        dto.world = deck.getWorldName();
        dto.anchorBlockX = deck.getAnchorBlockX();
        dto.anchorBlockY = deck.getAnchorBlockY();
        dto.anchorBlockZ = deck.getAnchorBlockZ();
        dto.mainDisplayEntityId = uuidToString(deck.getMainDisplayEntityId());
        dto.discardDisplayEntityId = uuidToString(deck.getDiscardDisplayEntityId());
        dto.trumpDisplayEntityId = uuidToString(deck.getTrumpDisplayEntityId());
        dto.mainInteractionEntityId = uuidToString(deck.getMainInteractionEntityId());
        dto.discardInteractionEntityId = uuidToString(deck.getDiscardInteractionEntityId());
        dto.mainDisplayYaw = deck.getMainDisplayYaw();
        dto.discardDisplayYaw = deck.getDiscardDisplayYaw();
        return dto;
    }

    private ActiveDeck fromDto(DeckDto dto) {
        try {
            UUID deckId = UUID.fromString(dto.deckId);
            // Decks saved before deck types existed had 36 cards, i.e. they are durak decks.
            DeckType type = dto.deckType == null ? DeckType.DURAK : DeckType.valueOf(dto.deckType);
            List<Card> definition = fromCardDtoList(dto.definition, deckId);
            ActiveDeck deck = new ActiveDeck(deckId, type, definition);
            deck.getMainPile().addAll(fromCardDtoList(dto.mainPile, deckId));
            deck.getDiscardPile().addAll(fromCardDtoList(dto.discardPile, deckId));

            deck.setWorldName(dto.world);
            deck.setAnchorBlockX(dto.anchorBlockX);
            deck.setAnchorBlockY(dto.anchorBlockY);
            deck.setAnchorBlockZ(dto.anchorBlockZ);
            deck.setMainDisplayEntityId(uuidFromString(dto.mainDisplayEntityId));
            deck.setDiscardDisplayEntityId(uuidFromString(dto.discardDisplayEntityId));
            deck.setTrumpDisplayEntityId(uuidFromString(dto.trumpDisplayEntityId));
            deck.setMainInteractionEntityId(uuidFromString(dto.mainInteractionEntityId));
            deck.setDiscardInteractionEntityId(uuidFromString(dto.discardInteractionEntityId));
            deck.setMainDisplayYaw(dto.mainDisplayYaw);
            deck.setDiscardDisplayYaw(dto.discardDisplayYaw);

            if (dto.revealedCards != null) {
                // Files written before stacks had ids: stacks were recognised by identical
                // X/Z, so give every distinct (world, x, z) one freshly generated stack id.
                Map<String, UUID> legacyStacks = new HashMap<>();
                for (RevealedCardDto revealedDto : dto.revealedCards) {
                    Card card = new Card(UUID.fromString(revealedDto.cardId), deckId, revealedDto.symbol);
                    World revealedWorld = revealedDto.world != null ? Bukkit.getWorld(revealedDto.world) : null;
                    Location loc = new Location(revealedWorld, revealedDto.x, revealedDto.y, revealedDto.z);

                    UUID stackId = uuidFromString(revealedDto.stackId);
                    if (stackId == null) {
                        String key = revealedDto.world + "|" + revealedDto.x + "|" + revealedDto.z;
                        stackId = legacyStacks.computeIfAbsent(key, k -> UUID.randomUUID());
                    }

                    RevealedCardInstance instance = new RevealedCardInstance(
                            card,
                            uuidFromString(revealedDto.entityUuid),
                            uuidFromString(revealedDto.interactionEntityUuid),
                            loc,
                            revealedDto.yaw,
                            stackId,
                            revealedDto.faceDown);
                    deck.getRevealedCards().add(instance);
                }
            }

            return deck;
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Failed to parse a stored deck entry, skipping it: " + ex.getMessage());
            return null;
        }
    }

    private List<CardDto> toCardDtoList(List<Card> cards) {
        List<CardDto> list = new ArrayList<>(cards.size());
        for (Card card : cards) {
            CardDto dto = new CardDto();
            dto.cardId = card.getCardId().toString();
            dto.symbol = card.getSymbol();
            list.add(dto);
        }
        return list;
    }

    private List<Card> fromCardDtoList(List<CardDto> dtos, UUID deckId) {
        List<Card> list = new ArrayList<>();
        if (dtos == null) {
            return list;
        }
        for (CardDto dto : dtos) {
            list.add(new Card(UUID.fromString(dto.cardId), deckId, dto.symbol));
        }
        return list;
    }

    private String uuidToString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private UUID uuidFromString(String raw) {
        return raw == null ? null : UUID.fromString(raw);
    }
}