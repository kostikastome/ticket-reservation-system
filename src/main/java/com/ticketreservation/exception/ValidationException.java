package com.ticketreservation.exception;

/** Thrown when user input breaks a business rule. */
public class ValidationException extends ReservationException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
