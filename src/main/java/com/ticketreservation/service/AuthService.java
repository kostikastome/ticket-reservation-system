package com.ticketreservation.service;

import com.ticketreservation.domain.User;
import com.ticketreservation.exception.AuthenticationException;
import com.ticketreservation.exception.ValidationException;
import com.ticketreservation.repository.UserRepository;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AuthService {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final PasswordHasher hasher;
    /** Hash of a random password, checked when the email is unknown so both cases take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordHasher hasher) {
        this.users = Objects.requireNonNull(users);
        this.hasher = Objects.requireNonNull(hasher);
        this.dummyHash = hasher.hash("not-a-real-password-0".toCharArray());
    }

    public User register(String email, char[] password, char[] passwordConfirmation) {
        try {
            String normalizedEmail = normalizeEmail(email);
            if (!isValidEmail(normalizedEmail)) {
                throw new ValidationException("Please enter a valid email address, e.g. name@example.com");
            }
            validatePassword(password);
            if (!Arrays.equals(password, passwordConfirmation)) {
                throw new ValidationException("The passwords do not match");
            }
            return users.create(normalizedEmail, hasher.hash(password));
        } finally {
            wipe(password);
            wipe(passwordConfirmation);
        }
    }

    /**
     * @throws AuthenticationException with the same message whether the email is unknown or the
     *                                 password is wrong, so attackers cannot find registered emails
     */
    public User login(String email, char[] password) {
        try {
            Optional<User> user = users.findByEmail(normalizeEmail(email));
            String storedHash = user.map(User::passwordHash).orElse(dummyHash);
            boolean passwordMatches = hasher.matches(password, storedHash);
            if (user.isEmpty() || !passwordMatches) {
                throw new AuthenticationException(INVALID_CREDENTIALS);
            }
            return user.get();
        } finally {
            wipe(password);
        }
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    /** Password rule: 8-128 characters with at least one letter and one digit. */
    public static void validatePassword(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("The password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            throw new ValidationException("The password must be at most " + MAX_PASSWORD_LENGTH + " characters long");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char ch : password) {
            hasLetter |= Character.isLetter(ch);
            hasDigit |= Character.isDigit(ch);
        }
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("The password must contain both letters and digits");
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void wipe(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }
}
