package com.ticketreservation.service;

import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Passenger;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * What a customer asks for when booking.
 *
 * @param returnFlightId empty for a one-way trip
 */
public record BookingRequest(
        long outboundFlightId,
        OptionalLong returnFlightId,
        FareClass fareClass,
        boolean checkedBaggage,
        List<Passenger> passengers) {

    public BookingRequest {
        Objects.requireNonNull(returnFlightId, "returnFlightId");
        Objects.requireNonNull(fareClass, "fareClass");
        passengers = List.copyOf(passengers);
    }

    public static BookingRequest oneWay(long flightId, FareClass fareClass, boolean checkedBaggage,
                                        List<Passenger> passengers) {
        return new BookingRequest(flightId, OptionalLong.empty(), fareClass, checkedBaggage, passengers);
    }

    public static BookingRequest roundTrip(long outboundId, long returnId, FareClass fareClass,
                                           boolean checkedBaggage, List<Passenger> passengers) {
        return new BookingRequest(outboundId, OptionalLong.of(returnId), fareClass, checkedBaggage, passengers);
    }
}
