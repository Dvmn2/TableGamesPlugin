package net.dvmn2.tablegamesplugin.util;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Plays the plugin's custom sound events (see {@link PluginSounds}) to every player
 * within a fixed radius of a point, rather than relying on {@link World#playSound}'s
 * volume-based falloff, which does not correspond to an exact block radius.
 */
public final class SoundUtil {

    private static final double RADIUS = 3.0;
    private static final double RADIUS_SQUARED = RADIUS * RADIUS;

    private static final float VOLUME = 1.0f;
    private static final float PITCH = 1.0f;

    private SoundUtil() {
    }

    /**
     * Plays {@code soundKey} (e.g. {@link PluginSounds#PUT_CARD_ON_TABLE}) at {@code location}, to every player within 3 blocks.
     */
    public static void play(Location location, String soundKey) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= RADIUS_SQUARED) {
                player.playSound(location, soundKey, SoundCategory.BLOCKS, VOLUME, PITCH);
            }
        }
    }
}