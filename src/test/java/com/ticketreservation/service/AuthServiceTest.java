package com.ticketreservation.service;

import com.ticketreservation.domain.User;
import com.ticketreservation.exception.AuthenticationException;
import com.ticketreservation.exception.DuplicateEmailException;
import com.ticketreservation.exception.ValidationException;
import com.ticketreservation.repository.memory.InMemoryRepositories;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private final AuthService auth = new AuthService(InMemoryRepositories.create().users(),
            new PasswordHasher(1_000, new SecureRandom()));

    private User register(String email, String password) {
        return auth.register(email, password.toCharArray(), password.toCharArray());
    }

    @Test
    void registeredUserCanLogIn() {
        User registered = register("Alice@Example.com ", "secret123");
        User loggedIn = auth.login("alice@example.com", "secret123".toCharArray());

        assertEquals(registered, loggedIn);
        assertEquals("alice@example.com", loggedIn.email(), "emails are normalised to lower case");
        assertFalse(loggedIn.passwordHash().contains("secret123"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() {
        register("alice@example.com", "secret123");

        AuthenticationException wrongPassword = assertThrows(AuthenticationException.class,
                () -> auth.login("alice@example.com", "secret124".toCharArray()));
        AuthenticationException unknownEmail = assertThrows(AuthenticationException.class,
                () -> auth.login("bob@example.com", "secret123".toCharArray()));

        assertEquals(wrongPassword.getMessage(), unknownEmail.getMessage());
    }

    @Test
    void emailCanOnlyBeRegisteredOnce() {
        register("alice@example.com", "secret123");
        assertThrows(DuplicateEmailException.class, () -> register("ALICE@example.com", "other456"));
    }

    @Test
    void rejectsInvalidEmails() {
        for (String email : new String[]{"", "alice", "alice@", "@example.com", "alice@example", "a b@example.com"}) {
            assertThrows(ValidationException.class, () -> register(email, "secret123"), email);
        }
        assertTrue(AuthService.isValidEmail("first.last+tag@sub.example.co.uk"));
    }

    @Test
    void enforcesPasswordRules() {
        assertThrows(ValidationException.class, () -> register("a@example.com", "short1"));
        assertThrows(ValidationException.class, () -> register("a@example.com", "onlyletters"));
        assertThrows(ValidationException.class, () -> register("a@example.com", "12345678"));
        assertThrows(ValidationException.class, () -> register("a@example.com", "a1".repeat(65)));
    }

    @Test
    void passwordsMustMatch() {
        assertThrows(ValidationException.class,
                () -> auth.register("a@example.com", "secret123".toCharArray(), "secret124".toCharArray()));
    }

    @Test
    void passwordArraysAreWipedAfterUse() {
        char[] password = "secret123".toCharArray();
        char[] confirmation = "secret123".toCharArray();
        auth.register("a@example.com", password, confirmation);
        assertEquals(new String(new char[9]), new String(password));
        assertEquals(new String(new char[9]), new String(confirmation));
    }
}
