package com.ticketreservation.repository.memory;

import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.User;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Shared state for the in-memory repositories.
 *
 * <p>Every read and write goes through {@link #locked}, which holds one lock. That keeps
 * multi-step operations (check seats, reserve them, store the booking) atomic. One global lock
 * is the simplest correct choice for a single-process demo store; the JDBC implementation relies
 * on database transactions instead.
 */
final class InMemoryStore {

    final Map<String, Airport> airports = new TreeMap<>();
    final Map<Long, Flight> flights = new HashMap<>();
    final Map<Long, Integer> availableSeats = new HashMap<>();
    final Map<Long, Booking> bookings = new LinkedHashMap<>();
    final Map<String, User> usersByEmail = new HashMap<>();

    private final ReentrantLock lock = new ReentrantLock();
    private long nextFlightId = 1;
    private long nextBookingId = 1;
    private long nextUserId = 1;

    <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    long nextFlightId() {
        return nextFlightId++;
    }

    long nextBookingId() {
        return nextBookingId++;
    }

    long nextUserId() {
        return nextUserId++;
    }
}
