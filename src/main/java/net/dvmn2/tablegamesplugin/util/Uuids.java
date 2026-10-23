package net.dvmn2.tablegamesplugin.util;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Safe UUID parsing for text read from items, entities and files. */
public final class Uuids {

    private Uuids() {
    }

    /** Returns the parsed UUID, or {@code null} when {@code raw} is {@code null} or malformed. */
    @Nullable
    public static UUID parseOrNull(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
