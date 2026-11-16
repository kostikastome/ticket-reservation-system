package com.ticketreservation.domain;

import java.time.LocalDateTime;

/**
 * A flight that has not been stored yet, so it has no id. The repository assigns the id and
 * starts the flight with every seat available.
 */
public record NewFlight(
        String flightNumber,
        Airport origin,
        Airport destination,
        LocalDateTime departure,
        LocalDateTime arrival,
        Aircraft aircraft,
        Money economyPrice,
        Money firstClassPrice) {

    public NewFlight {
        // Validate early by building a throwaway Flight with the same invariants.
        new Flight(0, flightNumber, origin, destination, departure, arrival, aircraft, economyPrice, firstClassPrice);
    }

    public Flight withId(long id) {
        return new Flight(id, flightNumber, origin, destination, departure, arrival, aircraft,
                economyPrice, firstClassPrice);
    }
}
