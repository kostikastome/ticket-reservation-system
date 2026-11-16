package com.ticketreservation.service;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Generates 6-character booking references such as {@code K7QX2M}, like airline PNR codes.
 *
 * <p>Characters that are easy to confuse when read aloud or typed (0/O, 1/I/L) are left out.
 * Using a {@link SecureRandom} means references cannot be guessed to look up other customers'
 * bookings.
 */
public final class BookingReferenceGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;

    private final RandomGenerator random;

    public BookingReferenceGenerator() {
        this(new SecureRandom());
    }

    public BookingReferenceGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    public String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
