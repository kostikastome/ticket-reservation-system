package com.ticketreservation.domain;

import java.util.regex.Pattern;

/**
 * A traveller on a booking. Validation lives here so an invalid passenger can never exist.
 */
public record Passenger(String firstName, String lastName, String phoneNumber) {

    /** Letters from any language, plus spaces, apostrophes and hyphens (e.g. "Anne-Marie", "O'Neil"). */
    private static final Pattern NAME = Pattern.compile("\\p{L}[\\p{L} '\\-]{0,49}");
    /** Optional leading '+', then 8 to 15 digits (E.164 length). */
    private static final Pattern PHONE = Pattern.compile("\\+?\\d{8,15}");

    public Passenger {
        firstName = normalize(firstName);
        lastName = normalize(lastName);
        phoneNumber = phoneNumber == null ? null : phoneNumber.replaceAll("[\\s\\-()]", "");
        if (!isValidName(firstName)) {
            throw new IllegalArgumentException("Invalid first name: " + firstName);
        }
        if (!isValidName(lastName)) {
            throw new IllegalArgumentException("Invalid last name: " + lastName);
        }
        if (!isValidPhone(phoneNumber)) {
            throw new IllegalArgumentException("Invalid phone number: " + phoneNumber);
        }
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(normalize(name)).matches();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone.replaceAll("[\\s\\-()]", "")).matches();
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }
}
