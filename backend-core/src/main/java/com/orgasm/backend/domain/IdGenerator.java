package com.orgasm.backend.domain;

import java.security.SecureRandom;

public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    // Crockford base32 lowercase: no ambiguous chars (i, l, o, u)
    private static final String ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz";
    private static final int LENGTH = 8;

    private IdGenerator() {}

    public static String generate(String prefix) {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        return prefix + "-" + new String(chars);
    }
}
