package com.ticketreservation.repository.memory;

import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.BookingStatus;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.NewBooking;
import com.ticketreservation.exception.SeatsUnavailableException;
import com.ticketreservation.repository.BookingRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryBookingRepository implements BookingRepository {

    private final InMemoryStore store;

    InMemoryBookingRepository(InMemoryStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public Booking create(NewBooking newBooking) {
        return store.locked(() -> {
            if (store.bookings.values().stream().anyMatch(b -> b.reference().equals(newBooking.reference()))) {
                throw new IllegalStateException("Duplicate booking reference: " + newBooking.reference());
            }
            int seats = newBooking.seatCount();
            Booking booking = newBooking.toBooking(0);

            // Check every flight first, then change anything: all-or-nothing.
            for (Flight flight : booking.flights()) {
                Integer free = store.availableSeats.get(flight.id());
                if (free == null) {
                    throw new IllegalArgumentException("Unknown flight id " + flight.id());
                }
                if (free < seats) {
                    throw new SeatsUnavailableException("Flight " + flight.flightNumber() + " has only "
                            + free + " seat(s) left");
                }
            }
            for (Flight flight : booking.flights()) {
                store.availableSeats.merge(flight.id(), -seats, Integer::sum);
            }
            Booking saved = newBooking.toBooking(store.nextBookingId());
            store.bookings.put(saved.id(), saved);
            return saved;
        });
    }

    @Override
    public Optional<Booking> cancel(long bookingId) {
        return store.locked(() -> {
            Booking booking = store.bookings.get(bookingId);
            if (booking == null || booking.status() != BookingStatus.CONFIRMED) {
                return Optional.empty();
            }
            for (Flight flight : booking.flights()) {
                store.availableSeats.merge(flight.id(), booking.seatCount(), Integer::sum);
            }
            Booking cancelled = booking.withStatus(BookingStatus.CANCELLED);
            store.bookings.put(bookingId, cancelled);
            return Optional.of(cancelled);
        });
    }

    @Override
    public Optional<Booking> findByReference(String reference) {
        return store.locked(() -> store.bookings.values().stream()
                .filter(b -> b.reference().equals(reference))
                .findFirst());
    }

    @Override
    public List<Booking> findByUserId(long userId) {
        return store.locked(() -> store.bookings.values().stream()
                .filter(b -> b.userId() == userId)
                .sorted(Comparator.comparing(Booking::createdAt).thenComparing(Booking::id).reversed())
                .toList());
    }
}
