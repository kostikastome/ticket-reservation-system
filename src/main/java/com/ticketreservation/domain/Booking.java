package com.ticketreservation.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A stored booking. The total price is fixed when the booking is made, so later fare changes do
 * not alter what the customer was charged.
 *
 * @param returnFlight {@code null} for a one-way trip
 */
public record Booking(
        long id,
        String reference,
        long userId,
        Flight outboundFlight,
        Flight returnFlight,
        FareClass fareClass,
        boolean checkedBaggage,
        List<Passenger> passengers,
        Money totalPrice,
        BookingStatus status,
        Instant createdAt) {

    public Booking {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(outboundFlight, "outboundFlight");
        Objects.requireNonNull(fareClass, "fareClass");
        Objects.requireNonNull(totalPrice, "totalPrice");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        passengers = List.copyOf(passengers);
    }

    public boolean isRoundTrip() {
        return returnFlight != null;
    }

    public Optional<Flight> returnFlightOptional() {
        return Optional.ofNullable(returnFlight);
    }

    /** The outbound flight followed by the return flight, if any. */
    public List<Flight> flights() {
        List<Flight> flights = new ArrayList<>(2);
        flights.add(outboundFlight);
        if (returnFlight != null) {
            flights.add(returnFlight);
        }
        return List.copyOf(flights);
    }

    public int seatCount() {
        return passengers.size();
    }

    public Booking withStatus(BookingStatus newStatus) {
        return new Booking(id, reference, userId, outboundFlight, returnFlight, fareClass, checkedBaggage,
                passengers, totalPrice, newStatus, createdAt);
    }
}
