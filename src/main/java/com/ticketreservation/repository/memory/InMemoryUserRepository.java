package com.ticketreservation.repository.memory;

import com.ticketreservation.domain.User;
import com.ticketreservation.exception.DuplicateEmailException;
import com.ticketreservation.repository.UserRepository;

import java.util.Objects;
import java.util.Optional;

public final class InMemoryUserRepository implements UserRepository {

    private final InMemoryStore store;

    InMemoryUserRepository(InMemoryStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public User create(String email, String passwordHash) {
        return store.locked(() -> {
            if (store.usersByEmail.containsKey(email)) {
                throw new DuplicateEmailException("An account with this email already exists");
            }
            User user = new User(store.nextUserId(), email, passwordHash);
            store.usersByEmail.put(email, user);
            return user;
        });
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return store.locked(() -> Optional.ofNullable(store.usersByEmail.get(email)));
    }
}
