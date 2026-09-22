package net.dvmn2.tablegamesplugin.util;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents a single physical player action (one click) from being processed more than
 * once by plugin logic. This guards against the well-documented Bukkit/Paper behaviour
 * of firing interaction events more than once for what a player perceives as a single
 * action (in addition to filtering by {@link org.bukkit.inventory.EquipmentSlot#HAND},
 * which is the primary defense used by the listeners).
 * <p>
 * Action keys frequently include a per-entity id (e.g. {@code "entity-" + uuid}), so over
 * a server's lifetime the set of distinct keys is effectively unbounded (revealed cards,
 * discarded decks, etc. are constantly replaced by new entities). {@link #sweepStaleEntries()}
 * must be called periodically (see the listeners' constructors) to bound the map's size.
 */
public final class InteractionGuard {

    private static final long DEBOUNCE_WINDOW_NANOS = 100_000_000L; // 100 ms

    /**
     * Entries not touched in this long are considered stale and eligible for removal by
     * {@link #sweepStaleEntries()}. Comfortably larger than the debounce window itself so
     * a sweep can never race with an in-flight debounce check.
     */
    private static final long STALE_ENTRY_NANOS = 60_000_000_000L; // 60 s

    private final Map<String, Long> lastHandledAt = new ConcurrentHashMap<>();

    /**
     * @return {@code true} the first time this action key is seen for this player within
     * the debounce window, {@code false} for a duplicate call that follows shortly after.
     */
    public boolean tryConsume(Player player, String actionKey) {
        String mapKey = player.getUniqueId() + "|" + actionKey;
        long now = System.nanoTime();
        Long previous = lastHandledAt.put(mapKey, now);
        return previous == null || (now - previous) > DEBOUNCE_WINDOW_NANOS;
    }

    /**
     * Removes every entry that hasn't been touched in {@link #STALE_ENTRY_NANOS}, so that
     * long-lived servers don't accumulate an ever-growing map of one-off entity/action
     * keys that will never be looked up again. Safe to call from any thread; backed by a
     * {@link ConcurrentHashMap}.
     */
    public void sweepStaleEntries() {
        long now = System.nanoTime();
        lastHandledAt.entrySet().removeIf(entry -> (now - entry.getValue()) > STALE_ENTRY_NANOS);
    }
}