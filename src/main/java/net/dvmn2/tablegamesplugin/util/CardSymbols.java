package net.dvmn2.tablegamesplugin.util;

/**
 * Card glyphs used by the plugin (Latin-1 Supplement code points, generated
 * programmatically to avoid any source-file encoding ambiguity).
 * <pre>
 * È (U+00C8)          - card back (reverse side)
 * É..ü (U+00C9..U+00FC) - 52 faces: ranks 2,3,4,5,6,7,8,9,10,J,Q,K,A x 4 suits,
 *                         four consecutive glyphs per rank
 * </pre>
 * Which contiguous slice of the 52 faces a particular deck uses is defined by
 * {@link net.dvmn2.tablegamesplugin.model.DeckType}.
 */
public final class CardSymbols {

    /**
     * The reverse side of a card (U+00C8, 'È'). Never a valid card identity: it is only
     * ever shown for face-down cards on the table.
     */
    public static final String BACK = String.valueOf((char) 0x00C8);

    /**
     * First face symbol: the 2 of the first suit (U+00C9, 'É').
     */
    public static final int FIRST_CODE_POINT = 0x00C9;

    /**
     * Consecutive glyphs per rank.
     */
    public static final int SUITS = 4;

    /**
     * Ranks 2..A times {@link #SUITS} = the complete face pool (U+00C9..U+00FC).
     */
    public static final int POOL_SIZE = 13 * SUITS;

    private CardSymbols() {
    }

    /**
     * {@code count} consecutive one-character symbols starting at {@code firstCodePoint}.
     */
    public static String[] range(int firstCodePoint, int count) {
        String[] symbols = new String[count];
        for (int i = 0; i < count; i++) {
            symbols[i] = String.valueOf((char) (firstCodePoint + i));
        }
        return symbols;
    }

    public static boolean isInRange(String symbol, int firstCodePoint, int count) {
        if (symbol == null || symbol.length() != 1) {
            return false;
        }
        char c = symbol.charAt(0);
        return c >= firstCodePoint && c < firstCodePoint + count;
    }

    /**
     * Whether {@code symbol} is a face of <em>any</em> supported deck type (the full
     * 52-symbol pool). The card back is deliberately not included.
     */
    public static boolean isValidSymbol(String symbol) {
        return isInRange(symbol, FIRST_CODE_POINT, POOL_SIZE);
    }
}