package com.ticketreservation.repository;

import com.ticketreservation.repository.jdbc.ConnectionProvider;
import com.ticketreservation.repository.jdbc.JdbcRepositories;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the repository contract against the JDBC implementation, using the production schema
 * on an H2 in-memory database in MySQL compatibility mode. Each test gets a fresh database.
 *
 * <p>To run against a real MySQL server instead, pass
 * {@code -Dtest.jdbc.url=jdbc:mysql://...} plus {@code -Dtest.jdbc.user} and
 * {@code -Dtest.jdbc.password} (the database must be empty).
 */
class JdbcRepositoryTest extends RepositoryContractTest {

    private Connection keepAlive;

    @Override
    protected Repositories createRepositories() throws SQLException {
        // "{db}" is replaced by a random name so every test starts with an empty database.
        String url = System.getProperty("test.jdbc.url",
                        "jdbc:h2:mem:{db};MODE=MySQL;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000")
                .replace("{db}", "test_" + UUID.randomUUID().toString().replace("-", ""));
        String user = System.getProperty("test.jdbc.user", "sa");
        String password = System.getProperty("test.jdbc.password", "");
        String schema = System.getProperty("test.jdbc.schema", JdbcRepositories.DEFAULT_SCHEMA);

        ConnectionProvider connections = ConnectionProvider.driverManager(url, user, password);
        // An in-memory database lives as long as one connection is open.
        keepAlive = DriverManager.getConnection(url, user, password);

        assertTrue(JdbcRepositories.initializeSchemaIfMissing(connections, schema), "schema created");
        assertFalse(JdbcRepositories.initializeSchemaIfMissing(connections, schema), "schema not created twice");
        return JdbcRepositories.create(connections);
    }

    @Override
    protected void tearDownStorage() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }
}
