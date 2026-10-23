package net.dvmn2.tablegamesplugin;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Plays the plugin's custom sound events to every player within a configured block radius.
 * <p>
 * {@link World#playSound} falls off by volume, which does not map to an exact block radius, so
 * players are filtered by distance here instead. Thread: main server thread.
 */
public final class SoundPlayer {

    private static final float VOLUME = 1.0f;
    private static final float PITCH = 1.0f;

    private final Supplier<TableGamesSettings> settings;

    public SoundPlayer(Supplier<TableGamesSettings> settings) {
        this.settings = settings;
    }

    /** Plays {@code soundKey} at {@code location}; does nothing when the location or its world is unavailable. */
    public void play(@Nullable Location location, String soundKey) {
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        double radius = settings.get().soundRadiusBlocks();
        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                player.playSound(location, soundKey, SoundCategory.BLOCKS, VOLUME, PITCH);
            }
        }
    }
}
