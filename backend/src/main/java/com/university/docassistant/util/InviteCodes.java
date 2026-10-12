package com.university.docassistant.util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Course invitation codes: 16 random bytes from SecureRandom, URL-safe Base64 without padding (22 characters,
 * 128 bits), so a code cannot be guessed and can sit in a link as-is.
 */
public final class InviteCodes {

    public static final int LENGTH = 22;
    /** What a code may look like; anything else is rejected before touching the database. */
    public static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{12,64}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {
    }

    public static String generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
