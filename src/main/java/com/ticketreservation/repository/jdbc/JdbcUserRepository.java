package com.ticketreservation.repository.jdbc;

import com.ticketreservation.domain.User;
import com.ticketreservation.exception.DataAccessException;
import com.ticketreservation.exception.DuplicateEmailException;
import com.ticketreservation.repository.UserRepository;

import java.sql.SQLException;
import java.util.Optional;

public final class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    JdbcUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public User create(String email, String passwordHash) {
        try {
            long id = jdbc.withConnection(c -> JdbcTemplate.insert(c,
                    "INSERT INTO users (email, password_hash) VALUES (?, ?)", email, passwordHash));
            return new User(id, email, passwordHash);
        } catch (DataAccessException e) {
            // The UNIQUE constraint on users.email is the source of truth, even under concurrent sign-ups.
            if (e.getCause() instanceof SQLException sql && JdbcTemplate.isConstraintViolation(sql)) {
                throw new DuplicateEmailException("An account with this email already exists");
            }
            throw e;
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jdbc.queryOne("SELECT id, email, password_hash FROM users WHERE email = ?",
                rs -> new User(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash")),
                email);
    }
}
