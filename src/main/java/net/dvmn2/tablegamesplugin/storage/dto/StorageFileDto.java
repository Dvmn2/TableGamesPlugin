package net.dvmn2.tablegamesplugin.storage.dto;

import java.util.List;

/** Root of {@code decks.json}. */
public final class StorageFileDto {

    /** Format version; absent (0) is accepted, newer than the plugin supports triggers a warning. */
    public int version;

    public List<DeckDto> decks;
}
