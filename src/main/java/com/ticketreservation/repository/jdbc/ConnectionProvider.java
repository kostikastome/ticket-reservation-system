package com.ticketreservation.repository.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Supplies database connections. A production deployment would plug a connection pool
 * (e.g. HikariCP) in here; {@link #driverManager} is enough for a desktop/CLI app.
 */
@FunctionalInterface
public interface ConnectionProvider {

    Connection getConnection() throws SQLException;

    static ConnectionProvider driverManager(String url, String user, String password) {
        return () -> DriverManager.getConnection(url, user, password);
    }
}
