package com.ticketreservation.domain;

import java.util.Objects;

/**
 * A registered customer. Only a salted password hash is stored, never the password itself.
 */
public record User(long id, String email, String passwordHash) {

    public User {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
    }

    /** Keeps the password hash out of logs and stack traces. */
    @Override
    public String toString() {
        return "User[id=" + id + ", email=" + email + "]";
    }
}
