package com.ticketreservation.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An airport identified by its three-letter IATA code (e.g. {@code TIA}).
 */
public record Airport(String code, String name, String city, String country) {

    private static final Pattern IATA_CODE = Pattern.compile("[A-Z]{3}");

    public Airport {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(country, "country");
        if (!IATA_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Airport code must be 3 upper-case letters: " + code);
        }
    }

    /** Short label for menus and tables, e.g. {@code "TIA - Tirana, Albania"}. */
    public String label() {
        return code + " - " + city + ", " + country;
    }
}
