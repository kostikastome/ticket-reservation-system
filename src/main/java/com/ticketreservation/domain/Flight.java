package com.ticketreservation.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A scheduled flight. Times are local airport times.
 *
 * <p>Flight instances are immutable snapshots of the schedule; the number of seats still
 * available is owned by the repository, because it changes concurrently with bookings.
 */
public record Flight(
        long id,
        String flightNumber,
        Airport origin,
        Airport destination,
        LocalDateTime departure,
        LocalDateTime arrival,
        Aircraft aircraft,
        Money economyPrice,
        Money firstClassPrice) {

    public Flight {
        Objects.requireNonNull(flightNumber, "flightNumber");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(departure, "departure");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(aircraft, "aircraft");
        Objects.requireNonNull(economyPrice, "economyPrice");
        Objects.requireNonNull(firstClassPrice, "firstClassPrice");
        if (flightNumber.isBlank()) {
            throw new IllegalArgumentException("Flight number cannot be blank");
        }
        if (origin.code().equals(destination.code())) {
            throw new IllegalArgumentException("Origin and destination must differ: " + origin.code());
        }
        if (!arrival.isAfter(departure)) {
            throw new IllegalArgumentException(
                    "Arrival (" + arrival + ") must be after departure (" + departure + ") for " + flightNumber);
        }
        if (!economyPrice.currency().equals(firstClassPrice.currency())) {
            throw new IllegalArgumentException("Economy and first-class prices must use the same currency");
        }
    }

    public Money priceFor(FareClass fareClass) {
        return switch (fareClass) {
            case ECONOMY -> economyPrice;
            case FIRST -> firstClassPrice;
        };
    }

    public Duration duration() {
        return Duration.between(departure, arrival);
    }

    /** Returns true if {@code other} flies the opposite direction of this flight. */
    public boolean isReverseRouteOf(Flight other) {
        return origin.code().equals(other.destination.code())
                && destination.code().equals(other.origin.code());
    }
}
