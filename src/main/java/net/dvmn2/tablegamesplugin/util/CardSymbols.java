package net.dvmn2.tablegamesplugin.util;

import org.jetbrains.annotations.Nullable;

/**
 * Card glyphs used by the plugin: Latin-1 Supplement code points, generated programmatically to
 * avoid source-file encoding ambiguity.
 * <pre>
 * U+00C8          card back (reverse side)
 * U+00C9..U+00FC  52 faces: ranks 2,3,4,5,6,7,8,9,10,J,Q,K,A, four consecutive glyphs per rank (suits)
 * </pre>
 * Which contiguous slice of the faces a deck uses is defined by
 * {@link net.dvmn2.tablegamesplugin.model.DeckType}.
 */
public final class CardSymbols {

    /** The reverse side of a card; never a valid card identity, shown only for face-down cards. */
    public static final String BACK = String.valueOf((char) 0x00C8);

    /** The first face glyph: the 2 of the first suit. */
    public static final int FIRST_CODE_POINT = 0x00C9;

    /** Consecutive glyphs per rank. */
    public static final int SUITS = 4;

    private static final int RANKS = 13;

    /** Number of faces in the complete pool (ranks 2..A times suits). */
    public static final int POOL_SIZE = RANKS * SUITS;

    private CardSymbols() {
    }

    /** {@code count} consecutive one-character symbols starting at {@code firstCodePoint}. */
    public static String[] range(int firstCodePoint, int count) {
        String[] symbols = new String[count];
        for (int i = 0; i < count; i++) {
            symbols[i] = String.valueOf((char) (firstCodePoint + i));
        }
        return symbols;
    }

    /** Whether {@code symbol} is one of {@code count} glyphs starting at {@code firstCodePoint}. */
    public static boolean isInRange(@Nullable String symbol, int firstCodePoint, int count) {
        if (symbol == null || symbol.length() != 1) {
            return false;
        }
        char c = symbol.charAt(0);
        return c >= firstCodePoint && c < firstCodePoint + count;
    }

    /** Whether {@code symbol} is a face of any supported deck; the card back is not included. */
    public static boolean isValidSymbol(@Nullable String symbol) {
        return isInRange(symbol, FIRST_CODE_POINT, POOL_SIZE);
    }
}
