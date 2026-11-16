package com.ticketreservation.repository.jdbc;

import com.ticketreservation.exception.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small helper that removes JDBC boilerplate: opening/closing connections, binding parameters,
 * mapping rows and running work inside a transaction. All SQL is parameterised, so user input is
 * never concatenated into a query.
 */
final class JdbcTemplate {

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final ConnectionProvider connections;

    JdbcTemplate(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    /** Runs {@code work} on one connection with auto-commit on. */
    <T> T withConnection(SqlWork<T> work) {
        try (Connection connection = connections.getConnection()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    /**
     * Runs {@code work} in one transaction. It is committed if {@code work} returns normally and
     * rolled back if it throws anything, including business exceptions.
     *
     * <p>If the database aborts the transaction because of a deadlock or a write conflict with
     * another transaction, the whole transaction is retried a few times. That is safe because
     * nothing from the failed attempt was committed.
     */
    <T> T inTransaction(SqlWork<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return runTransaction(work);
            } catch (SQLException e) {
                if (!isTransient(e) || attempt >= MAX_TRANSACTION_ATTEMPTS) {
                    throw new DataAccessException("Database error: " + e.getMessage(), e);
                }
                backOff(attempt);
            }
        }
    }

    static final int MAX_TRANSACTION_ATTEMPTS = 5;

    private <T> T runTransaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = connections.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection, e);
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /**
     * SQLSTATE class 40 = transaction rollback (serialization failure, deadlock). H2 reports
     * write-write conflicts with its own code 90131.
     */
    static boolean isTransient(SQLException e) {
        for (SQLException current = e; current != null; current = current.getNextException()) {
            String state = current.getSQLState();
            if (state != null && (state.startsWith("40") || state.equals("90131"))) {
                return true;
            }
        }
        return false;
    }

    private static void backOff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 20) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("Interrupted while retrying a transaction", null);
        }
    }

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        return withConnection(c -> query(c, sql, mapper, params));
    }

    <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return withConnection(c -> queryOne(c, sql, mapper, params));
    }

    int update(String sql, Object... params) {
        return withConnection(c -> update(c, sql, params));
    }

    static <T> List<T> query(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
            return rows;
        }
    }

    static <T> Optional<T> queryOne(Connection c, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        List<T> rows = query(c, sql, mapper, params);
        if (rows.size() > 1) {
            throw new SQLException("Expected at most one row but got " + rows.size());
        }
        return rows.stream().findFirst();
    }

    static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params)) {
            return ps.executeUpdate();
        }
    }

    /** Executes an INSERT and returns the generated numeric primary key. */
    static long insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for: " + sql);
                }
                return keys.getLong(1);
            }
        }
    }

    static boolean isConstraintViolation(SQLException e) {
        // SQLSTATE class 23 = integrity constraint violation (unique key, foreign key, check).
        for (SQLException current = e; current != null; current = current.getNextException()) {
            String state = current.getSQLState();
            if (state != null && state.startsWith("23")) {
                return true;
            }
        }
        return false;
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        try {
            bind(ps, params);
            return ps;
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof SqlNull sqlNull) {
                ps.setNull(i + 1, sqlNull.sqlType());
            } else if (params[i] == null) {
                throw new IllegalArgumentException("Use SqlNull for null parameter #" + (i + 1));
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    /** A typed SQL NULL parameter; some drivers need the column type to bind a null. */
    record SqlNull(int sqlType) {
    }

    private static void rollbackQuietly(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
