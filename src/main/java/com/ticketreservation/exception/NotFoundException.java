package com.ticketreservation.exception;

/** Thrown when a flight, booking or user does not exist (or is not visible to the caller). */
public class NotFoundException extends ReservationException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
