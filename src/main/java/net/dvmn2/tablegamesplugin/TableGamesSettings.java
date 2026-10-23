package net.dvmn2.tablegamesplugin;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * @param language            {@code ru}, {@code en} or {@code auto}
 * @param saveIntervalSeconds how often changed state is written to disk, seconds
 * @param revealRadiusBlocks  maximum deck-to-click distance for putting a card on the table, blocks
 * @param soundRadiusBlocks   radius within which players hear plugin sounds, blocks
 */
public record TableGamesSettings(
        String language,
        int saveIntervalSeconds,
        double revealRadiusBlocks,
        double soundRadiusBlocks) {

    private static final String ROOT = "settings.";

    private static final String DEFAULT_LANGUAGE = "auto";
    private static final int DEFAULT_SAVE_INTERVAL_SECONDS = 5;
    private static final int MIN_SAVE_INTERVAL_SECONDS = 1;
    private static final int MAX_SAVE_INTERVAL_SECONDS = 3600;
    private static final double DEFAULT_REVEAL_RADIUS_BLOCKS = 3.0;
    private static final double MIN_REVEAL_RADIUS_BLOCKS = 0.5;
    private static final double MAX_REVEAL_RADIUS_BLOCKS = 16.0;
    private static final double DEFAULT_SOUND_RADIUS_BLOCKS = 3.0;
    private static final double MIN_SOUND_RADIUS_BLOCKS = 1.0;
    private static final double MAX_SOUND_RADIUS_BLOCKS = 64.0;

    /** Reads the settings; invalid values are reported with a warning and replaced by defaults. */
    public static TableGamesSettings parse(ConfigurationSection config, Logger log) {
        return new TableGamesSettings(
                readLanguage(config, log),
                readInt(config, log, "save-interval-seconds", DEFAULT_SAVE_INTERVAL_SECONDS,
                        MIN_SAVE_INTERVAL_SECONDS, MAX_SAVE_INTERVAL_SECONDS),
                readDouble(config, log, "reveal-radius-blocks", DEFAULT_REVEAL_RADIUS_BLOCKS,
                        MIN_REVEAL_RADIUS_BLOCKS, MAX_REVEAL_RADIUS_BLOCKS),
                readDouble(config, log, "sound-radius-blocks", DEFAULT_SOUND_RADIUS_BLOCKS,
                        MIN_SOUND_RADIUS_BLOCKS, MAX_SOUND_RADIUS_BLOCKS));
    }

    private static String readLanguage(ConfigurationSection config, Logger log) {
        String raw = config.getString(ROOT + "language", DEFAULT_LANGUAGE);
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.equals("ru") || value.equals("en") || value.equals("auto")) {
            return value;
        }
        log.warning(ROOT + "language must be ru, en or auto, got '" + raw + "'; using " + DEFAULT_LANGUAGE);
        return DEFAULT_LANGUAGE;
    }

    private static int readInt(ConfigurationSection config, Logger log, String key, int fallback, int min, int max) {
        int value = config.getInt(ROOT + key, fallback);
        if (value < min || value > max) {
            log.warning(ROOT + key + " must be in " + min + ".." + max + ", got " + value
                    + "; using " + fallback);
            return fallback;
        }
        return value;
    }

    private static double readDouble(ConfigurationSection config, Logger log, String key, double fallback,
                                     double min, double max) {
        double value = config.getDouble(ROOT + key, fallback);
        // The negated form also rejects NaN.
        if (!(value >= min && value <= max)) {
            log.warning(ROOT + key + " must be in " + min + ".." + max + ", got " + value
                    + "; using " + fallback);
            return fallback;
        }
        return value;
    }
}
