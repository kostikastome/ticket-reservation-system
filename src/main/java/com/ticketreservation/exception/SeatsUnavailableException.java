package com.ticketreservation.exception;

/** Thrown when a flight no longer has enough free seats for a booking. */
public class SeatsUnavailableException extends ReservationException {

    private static final long serialVersionUID = 1L;

    public SeatsUnavailableException(String message) {
        super(message);
    }
}
