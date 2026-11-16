package com.ticketreservation.repository;

import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.NewBooking;
import com.ticketreservation.exception.SeatsUnavailableException;

import java.util.List;
import java.util.Optional;

/**
 * Stores bookings. Implementations must make {@link #create} and {@link #cancel} atomic: either
 * the booking and all of its seat changes are saved, or none of them are. They must also be safe
 * when many customers book the same flight at the same time (no overbooking).
 */
public interface BookingRepository {

    /**
     * Reserves {@code booking.seatCount()} seats on every flight in the booking and stores it.
     *
     * @throws SeatsUnavailableException if any flight does not have enough free seats; nothing
     *                                   is changed in that case
     */
    Booking create(NewBooking booking);

    /**
     * Marks a confirmed booking as cancelled and gives its seats back.
     *
     * @return the cancelled booking, or empty if no confirmed booking has that id (for example
     *         because it was already cancelled)
     */
    Optional<Booking> cancel(long bookingId);

    Optional<Booking> findByReference(String reference);

    /** The user's bookings, newest first. */
    List<Booking> findByUserId(long userId);
}
