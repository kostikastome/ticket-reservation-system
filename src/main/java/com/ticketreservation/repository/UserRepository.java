package com.ticketreservation.repository;

import com.ticketreservation.domain.User;
import com.ticketreservation.exception.DuplicateEmailException;

import java.util.Optional;

public interface UserRepository {

    /**
     * Creates a user. Emails are unique.
     *
     * @throws DuplicateEmailException if the email is already registered
     */
    User create(String email, String passwordHash);

    Optional<User> findByEmail(String email);
}
