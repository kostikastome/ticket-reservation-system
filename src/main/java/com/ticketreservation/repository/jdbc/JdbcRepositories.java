package com.ticketreservation.repository.jdbc;

import com.ticketreservation.exception.DataAccessException;
import com.ticketreservation.repository.Repositories;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Creates JDBC-backed repositories and, if needed, the database schema.
 */
public final class JdbcRepositories {

    /** Default schema, written for MySQL 8 (also runs on H2 in MySQL mode). */
    public static final String DEFAULT_SCHEMA = "/db/schema.sql";

    private JdbcRepositories() {
    }

    public static Repositories create(ConnectionProvider connections) {
        JdbcTemplate jdbc = new JdbcTemplate(connections);
        JdbcFlightRepository flights = new JdbcFlightRepository(jdbc);
        return new Repositories(flights, new JdbcBookingRepository(jdbc, flights), new JdbcUserRepository(jdbc));
    }

    /**
     * Runs the schema script if the {@code flights} table does not exist yet.
     *
     * @return true if the schema was created, false if it was already there
     */
    public static boolean initializeSchemaIfMissing(ConnectionProvider connections, String schemaResource) {
        try (Connection c = connections.getConnection()) {
            if (tableExists(c, "flights")) {
                return false;
            }
            try (Statement statement = c.createStatement()) {
                for (String sql : readStatements(schemaResource)) {
                    statement.execute(sql);
                }
            }
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Could not create database schema: " + e.getMessage(), e);
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        // Databases differ in how they store identifier case (MySQL: as written, H2/Derby: upper case).
        for (String name : List.of(table, table.toUpperCase())) {
            try (ResultSet rs = meta.getTables(c.getCatalog(), null, name, new String[]{"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<String> readStatements(String resource) {
        try (InputStream in = JdbcRepositories.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, "Schema resource not found: " + resource);
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String withoutComments = script.lines()
                    .map(line -> line.replaceFirst("--.*$", ""))
                    .reduce("", (a, b) -> a + b + "\n");
            return Arrays.stream(withoutComments.split(";"))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new DataAccessException("Could not read " + resource, e);
        }
    }
}
