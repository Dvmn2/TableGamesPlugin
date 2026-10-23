package net.dvmn2.tablegamesplugin.model;

import org.jetbrains.annotations.Nullable;

/** What a plugin-owned entity is for; the id is stored under the {@code type} key of its data container. */
public enum EntityRole {

    /** The main pile (display or click hitbox). */
    DECK("deck"),
    /** The discard pile (display or click hitbox). */
    DISCARD("discard"),
    /** The text display showing the trump card; not clickable. */
    TRUMP("trump_display"),
    /** A card lying on the table (display or click hitbox). */
    REVEALED_CARD("revealed_card");

    private final String id;

    EntityRole(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** Returns the role stored under {@code id}, or {@code null} for an unknown or missing id. */
    @Nullable
    public static EntityRole fromId(@Nullable String id) {
        for (EntityRole role : values()) {
            if (role.id.equals(id)) {
                return role;
            }
        }
        return null;
    }
}
