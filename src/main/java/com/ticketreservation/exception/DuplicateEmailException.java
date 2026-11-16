package com.ticketreservation.exception;

/** Thrown when someone tries to register with an email that already has an account. */
public class DuplicateEmailException extends ReservationException {

    private static final long serialVersionUID = 1L;

    public DuplicateEmailException(String message) {
        super(message);
    }
}
