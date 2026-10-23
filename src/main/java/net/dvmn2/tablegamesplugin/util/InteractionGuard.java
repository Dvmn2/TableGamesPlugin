package net.dvmn2.tablegamesplugin.util;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes sure one physical click is processed once by plugin logic.
 * <p>
 * Bukkit may fire interaction events more than once for a single click. Listeners filter by hand
 * first; this debounce catches the remaining duplicates. Action keys often contain entity ids, so
 * the set of distinct keys is unbounded over time and {@link #sweepStaleEntries()} must be called
 * periodically. Thread: main server thread.
 */
public final class InteractionGuard {

    /** Duplicate-click window, nanoseconds (100 ms). */
    private static final long DEBOUNCE_WINDOW_NANOS = 100_000_000L;

    /** Entries untouched for this long are dropped by a sweep, nanoseconds (60 s); far above the debounce window. */
    private static final long STALE_ENTRY_NANOS = 60_000_000_000L;

    private final Map<String, Long> lastHandledAt = new ConcurrentHashMap<>();

    /**
     * Registers the action and tells whether it should be handled.
     *
     * @return {@code true} the first time {@code actionKey} is seen for this player within the debounce
     * window, {@code false} for a duplicate that follows shortly after
     */
    public boolean tryConsume(Player player, String actionKey) {
        String mapKey = player.getUniqueId() + "|" + actionKey;
        long now = System.nanoTime();
        Long previous = lastHandledAt.put(mapKey, now);
        return previous == null || (now - previous) > DEBOUNCE_WINDOW_NANOS;
    }

    /** Removes entries that have not been touched for {@link #STALE_ENTRY_NANOS}. */
    public void sweepStaleEntries() {
        long now = System.nanoTime();
        lastHandledAt.entrySet().removeIf(entry -> (now - entry.getValue()) > STALE_ENTRY_NANOS);
    }
}
