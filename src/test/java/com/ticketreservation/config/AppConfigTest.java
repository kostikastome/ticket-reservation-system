package com.ticketreservation.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void defaultsToInMemoryStorage() {
        AppConfig config = AppConfig.from(Map.of());
        assertEquals(AppConfig.Storage.MEMORY, config.storage());
        assertEquals(AppConfig.DEFAULT_DB_URL, config.dbUrl());
        assertTrue(config.seedDemoData());
        assertEquals(AppConfig.DEFAULT_PORT, config.port());
    }

    @Test
    void readsDatabaseSettings() {
        AppConfig config = AppConfig.from(Map.of(
                "TR_STORAGE", "jdbc",
                "TR_DB_URL", "jdbc:mysql://db:3306/tickets",
                "TR_DB_USER", "app",
                "TR_DB_PASSWORD", "s3cret",
                "TR_SEED_DEMO_DATA", "false"));

        assertEquals(AppConfig.Storage.JDBC, config.storage());
        assertEquals("jdbc:mysql://db:3306/tickets", config.dbUrl());
        assertEquals("app", config.dbUser());
        assertEquals("s3cret", config.dbPassword());
        assertFalse(config.seedDemoData());
    }

    @Test
    void neverPrintsThePassword() {
        AppConfig config = AppConfig.from(Map.of("TR_DB_PASSWORD", "s3cret"));
        assertFalse(config.toString().contains("s3cret"));
    }

    @Test
    void readsAndValidatesThePort() {
        assertEquals(9090, AppConfig.from(Map.of("TR_PORT", "9090")).port());
        assertThrows(IllegalArgumentException.class, () -> AppConfig.from(Map.of("TR_PORT", "abc")));
        assertThrows(IllegalArgumentException.class, () -> AppConfig.from(Map.of("TR_PORT", "70000")));
    }

    @Test
    void rejectsUnknownStorage() {
        assertThrows(IllegalArgumentException.class, () -> AppConfig.from(Map.of("TR_STORAGE", "redis")));
    }
}
