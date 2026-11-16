package com.ticketreservation.repository.jdbc;

import com.ticketreservation.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransactionRollbackException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTemplateTest {

    private final AtomicInteger commits = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();

    /** A fake connection that only records commit/rollback calls. */
    private Connection fakeConnection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "commit" -> {
                        commits.incrementAndGet();
                        yield null;
                    }
                    case "rollback" -> {
                        rollbacks.incrementAndGet();
                        yield null;
                    }
                    case "getAutoCommit" -> true;
                    default -> null;
                });
    }

    private final JdbcTemplate jdbc = new JdbcTemplate(this::fakeConnection);

    @Test
    void retriesTransactionsAbortedByDeadlocks() {
        AtomicInteger attempts = new AtomicInteger();

        String result = jdbc.inTransaction(c -> {
            if (attempts.incrementAndGet() < 3) {
                throw new SQLTransactionRollbackException("Deadlock found", "40001");
            }
            return "done";
        });

        assertEquals("done", result);
        assertEquals(3, attempts.get());
        assertEquals(2, rollbacks.get());
        assertEquals(1, commits.get());
    }

    @Test
    void givesUpAfterTooManyTransientFailures() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(DataAccessException.class, () -> jdbc.inTransaction(c -> {
            attempts.incrementAndGet();
            throw new SQLException("Concurrent update", "90131");
        }));
        assertEquals(JdbcTemplate.MAX_TRANSACTION_ATTEMPTS, attempts.get());
        assertEquals(0, commits.get());
    }

    @Test
    void doesNotRetryOtherErrorsAndRollsBackBusinessExceptions() {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(DataAccessException.class, () -> jdbc.inTransaction(c -> {
            attempts.incrementAndGet();
            throw new SQLIntegrityConstraintViolationException("duplicate", "23000");
        }));
        assertEquals(1, attempts.get());

        assertThrows(IllegalStateException.class, () -> jdbc.inTransaction(c -> {
            throw new IllegalStateException("business rule");
        }));
        assertEquals(2, rollbacks.get());
        assertEquals(0, commits.get());
    }

    @Test
    void classifiesSqlStates() {
        assertTrue(JdbcTemplate.isConstraintViolation(new SQLException("dup", "23505")));
        assertFalse(JdbcTemplate.isConstraintViolation(new SQLException("x", "42000")));
        assertTrue(JdbcTemplate.isTransient(new SQLException("deadlock", "40P01")));
        assertFalse(JdbcTemplate.isTransient(new SQLException("syntax", "42000")));
        assertFalse(JdbcTemplate.isTransient(new SQLException("no state")));
    }

    @Test
    void splitsSchemaScriptIntoStatements() {
        List<String> statements = JdbcRepositories.readStatements(JdbcRepositories.DEFAULT_SCHEMA);
        assertEquals(8, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE airports"));
        assertTrue(statements.stream().noneMatch(s -> s.contains("--")));
    }
}
