package com.ticketreservation.config;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime configuration, read from environment variables so no credentials live in the code.
 *
 * <table>
 *   <caption>Supported variables</caption>
 *   <tr><th>Variable</th><th>Default</th><th>Meaning</th></tr>
 *   <tr><td>TR_STORAGE</td><td>memory</td><td>{@code memory} (no setup) or {@code jdbc}</td></tr>
 *   <tr><td>TR_DB_URL</td><td>jdbc:mysql://localhost:3306/ticket_reservation</td><td>JDBC URL</td></tr>
 *   <tr><td>TR_DB_USER</td><td>ticket_app</td><td>database user</td></tr>
 *   <tr><td>TR_DB_PASSWORD</td><td>(empty)</td><td>database password</td></tr>
 *   <tr><td>TR_SEED_DEMO_DATA</td><td>true</td><td>create demo flights when none are scheduled</td></tr>
 *   <tr><td>TR_PORT</td><td>8080</td><td>port of the web UI (always bound to localhost)</td></tr>
 * </table>
 */
public record AppConfig(Storage storage, String dbUrl, String dbUser, String dbPassword, boolean seedDemoData,
                        int port) {

    public enum Storage { MEMORY, JDBC }

    public static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/ticket_reservation";
    public static final String DEFAULT_DB_USER = "ticket_app";
    public static final int DEFAULT_PORT = 8080;

    public AppConfig {
        Objects.requireNonNull(storage, "storage");
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("TR_PORT must be between 0 and 65535 but was " + port);
        }
    }

    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static AppConfig from(Map<String, String> env) {
        String storageValue = env.getOrDefault("TR_STORAGE", "memory").trim().toUpperCase(Locale.ROOT);
        Storage storage = switch (storageValue) {
            case "MEMORY", "" -> Storage.MEMORY;
            case "JDBC", "MYSQL", "DATABASE", "DB" -> Storage.JDBC;
            default -> throw new IllegalArgumentException(
                    "TR_STORAGE must be 'memory' or 'jdbc' but was '" + env.get("TR_STORAGE") + "'");
        };
        return new AppConfig(
                storage,
                env.getOrDefault("TR_DB_URL", DEFAULT_DB_URL),
                env.getOrDefault("TR_DB_USER", DEFAULT_DB_USER),
                env.getOrDefault("TR_DB_PASSWORD", ""),
                Boolean.parseBoolean(env.getOrDefault("TR_SEED_DEMO_DATA", "true")),
                parsePort(env.getOrDefault("TR_PORT", String.valueOf(DEFAULT_PORT))));
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("TR_PORT must be a number but was '" + value + "'");
        }
    }

    /** Keeps the database password out of logs. */
    @Override
    public String toString() {
        return "AppConfig[storage=" + storage + ", dbUrl=" + dbUrl + ", dbUser=" + dbUser
                + ", dbPassword=***, seedDemoData=" + seedDemoData + ", port=" + port + "]";
    }
}
