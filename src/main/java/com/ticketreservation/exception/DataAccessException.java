package com.ticketreservation.exception;

/**
 * Wraps low-level storage failures (e.g. {@link java.sql.SQLException}) so callers above the
 * repository layer do not depend on JDBC.
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
