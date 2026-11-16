package com.ticketreservation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything needed to store a booking. The repository reserves the seats and assigns the id
 * in a single atomic step.
 *
 * @param returnFlight {@code null} for a one-way trip
 */
public record NewBooking(
        String reference,
        long userId,
        Flight outboundFlight,
        Flight returnFlight,
        FareClass fareClass,
        boolean checkedBaggage,
        List<Passenger> passengers,
        Money totalPrice,
        Instant createdAt) {

    public NewBooking {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(outboundFlight, "outboundFlight");
        Objects.requireNonNull(fareClass, "fareClass");
        Objects.requireNonNull(totalPrice, "totalPrice");
        Objects.requireNonNull(createdAt, "createdAt");
        passengers = List.copyOf(passengers);
        if (passengers.isEmpty()) {
            throw new IllegalArgumentException("A booking needs at least one passenger");
        }
    }

    public Optional<Flight> returnFlightOptional() {
        return Optional.ofNullable(returnFlight);
    }

    public int seatCount() {
        return passengers.size();
    }

    public Booking toBooking(long id) {
        return new Booking(id, reference, userId, outboundFlight, returnFlight, fareClass, checkedBaggage,
                passengers, totalPrice, BookingStatus.CONFIRMED, createdAt);
    }
}
