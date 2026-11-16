package com.ticketreservation.repository;

/**
 * The set of repositories the application runs on, backed by one storage engine.
 */
public record Repositories(FlightRepository flights, BookingRepository bookings, UserRepository users) {
}
