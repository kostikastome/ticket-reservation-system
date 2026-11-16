package com.ticketreservation.exception;

/**
 * Base class for business errors that should be shown to the user (as opposed to bugs or
 * infrastructure failures).
 */
public class ReservationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReservationException(String message) {
        super(message);
    }

    public ReservationException(String message, Throwable cause) {
        super(message, cause);
    }
}
