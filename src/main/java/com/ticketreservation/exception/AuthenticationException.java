package com.ticketreservation.exception;

/** Thrown when a login fails. The message never says whether the email or the password was wrong. */
public class AuthenticationException extends ReservationException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }
}
