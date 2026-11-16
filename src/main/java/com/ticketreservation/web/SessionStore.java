package com.ticketreservation.web;

import com.ticketreservation.domain.User;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logged-in sessions, identified by random bearer tokens. Tokens are 256-bit values from
 * {@link SecureRandom}, so they cannot be guessed, and they expire after {@link #TTL}.
 */
public final class SessionStore {

    public static final Duration TTL = Duration.ofHours(8);

    private record Session(User user, Instant expiresAt) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public SessionStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public String create(User user) {
        removeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user, clock.instant().plus(TTL)));
        return token;
    }

    public Optional<User> find(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.user());
    }

    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private void removeExpired() {
        Instant now = clock.instant();
        sessions.values().removeIf(s -> !now.isBefore(s.expiresAt()));
    }
}
