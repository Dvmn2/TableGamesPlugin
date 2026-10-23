package net.dvmn2.tablegamesplugin.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * The block a deck stands on. The main pile sits on top of it and the discard pile on top of the
 * block one step to the east (+X).
 *
 * @param world the world name
 * @param x     block X
 * @param y     block Y
 * @param z     block Z
 */
public record Anchor(String world, int x, int y, int z) {

    private static final double CENTER_OFFSET = 0.5;
    private static final double SURFACE_OFFSET = 1.0;

    /** The centre of the main pile's top surface, or {@code null} when the world is not loaded. */
    @Nullable
    public Location mainCenter() {
        return surface(0);
    }

    /** The centre of the discard pile's top surface, or {@code null} when the world is not loaded. */
    @Nullable
    public Location discardCenter() {
        return surface(1);
    }

    @Nullable
    private Location surface(int eastOffset) {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x + eastOffset + CENTER_OFFSET, y + SURFACE_OFFSET, z + CENTER_OFFSET);
    }
}
