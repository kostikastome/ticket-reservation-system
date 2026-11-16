package com.ticketreservation.domain;

import java.util.Objects;

/**
 * A flight together with the number of seats that can still be booked on it.
 */
public record FlightAvailability(Flight flight, int availableSeats) {

    public FlightAvailability {
        Objects.requireNonNull(flight, "flight");
        if (availableSeats < 0 || availableSeats > flight.aircraft().capacity()) {
            throw new IllegalArgumentException("Invalid seat count " + availableSeats + " for " + flight.flightNumber());
        }
    }

    public boolean canSeat(int passengers) {
        return availableSeats >= passengers;
    }
}
