package com.university.docassistant.util;

import java.util.Locale;

/**
 * Case-insensitive "contains" patterns for JPQL {@code LIKE ... ESCAPE '\'}: the user's % and _ are matched
 * literally instead of acting as wildcards.
 */
public final class LikePatterns {

    /** Search terms shorter than this are ignored (too broad, and costly on large tables). */
    public static final int MIN_TERM_LENGTH = 2;

    private LikePatterns() {
    }

    /** The term as a lower-case contains-pattern, or null when it is blank or shorter than {@link #MIN_TERM_LENGTH}. */
    public static String contains(String term) {
        if (term == null) {
            return null;
        }
        String trimmed = term.strip();
        if (trimmed.length() < MIN_TERM_LENGTH) {
            return null;
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
