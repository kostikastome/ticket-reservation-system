package com.ticketreservation.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    /** Few iterations keep the test fast; production uses {@link PasswordHasher#DEFAULT_ITERATIONS}. */
    private final PasswordHasher hasher = new PasswordHasher(1_000, new SecureRandom());

    @Test
    void verifiesTheCorrectPasswordOnly() {
        String hash = hasher.hash("correct horse 1".toCharArray());
        assertTrue(hasher.matches("correct horse 1".toCharArray(), hash));
        assertFalse(hasher.matches("correct horse 2".toCharArray(), hash));
    }

    @Test
    void neverStoresThePlainPassword() {
        String hash = hasher.hash("secret123".toCharArray());
        assertFalse(hash.contains("secret123"));
        assertTrue(hash.startsWith("pbkdf2-sha256$1000$"));
    }

    @Test
    void usesARandomSaltPerHash() {
        assertNotEquals(hasher.hash("secret123".toCharArray()), hasher.hash("secret123".toCharArray()));
    }

    @Test
    void hashesWithDifferentIterationCountsStillVerify() {
        String oldHash = new PasswordHasher(500, new SecureRandom()).hash("secret123".toCharArray());
        assertTrue(hasher.matches("secret123".toCharArray(), oldHash));
    }

    @Test
    void malformedHashesNeverMatch() {
        assertFalse(hasher.matches("x".toCharArray(), "plain-text-password"));
        assertFalse(hasher.matches("x".toCharArray(), "pbkdf2-sha256$abc$!!$!!"));
        assertFalse(hasher.matches("x".toCharArray(), null));
        assertFalse(hasher.matches(null, hasher.hash("x1".toCharArray())));
    }
}
