package com.pi2.anchor.backend.domain;

import java.util.concurrent.ThreadLocalRandom;

public final class IdGenerator {

    // Custom epoch (2023-11-14) keeps generated values compact and far from Long overflow
    private static final long CUSTOM_EPOCH = 1_700_000_000_000L;

    // Configurable via IdGeneratorConfig; default 0 is safe for local/test environments
    private static volatile long secretKey = 0L;

    private IdGenerator() {}

    /** Called once at startup by IdGeneratorConfig with the value from {@code app.id.secret}. */
    public static void configure(long key) {
        secretKey = key;
    }

    /**
     * Generates a sortable 64-bit ID: 42 ms bits (from custom epoch) + 22 random bits.
     * Always positive. Collision probability under 1-in-4M per millisecond per JVM.
     */
    public static long generate() {
        long offset = System.currentTimeMillis() - CUSTOM_EPOCH;
        long random = ThreadLocalRandom.current().nextLong() & 0x3FFFFFL;
        return (offset << 22) | random;
    }

    /**
     * Formats a DB Long as the user-visible prefixed ID.
     * The numeric value is XOR'd with a secret key then bit-reversed so the DB sequence
     * is not apparent from the external representation.
     */
    public static String format(String prefix, long id) {
        long scrambled = Long.reverse(id ^ secretKey);
        return prefix + "-" + String.format("%016x", scrambled);
    }

    /** Parses a prefixed ID back to the DB Long. Inverse of {@link #format}. */
    public static long parse(String prefixedId) {
        int dash = prefixedId.lastIndexOf('-');
        long scrambled = Long.parseUnsignedLong(prefixedId.substring(dash + 1), 16);
        return Long.reverse(scrambled) ^ secretKey;
    }
}
