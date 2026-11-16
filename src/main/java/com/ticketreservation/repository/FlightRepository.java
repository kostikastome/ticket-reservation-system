package com.ticketreservation.repository;

import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.NewFlight;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Stores airports and the flight schedule, and reports seat availability.
 *
 * <p>Seats are reserved and released only through {@link BookingRepository}, so that a booking
 * and its seats are always written together.
 */
public interface FlightRepository {

    Airport saveAirport(Airport airport);

    List<Airport> findAllAirports();

    Optional<Airport> findAirport(String code);

    /** Stores a flight with all of its aircraft's seats available. */
    Flight saveFlight(NewFlight flight);

    /** Stores many flights; implementations may do this in one batch. */
    default List<Flight> saveFlights(List<NewFlight> newFlights) {
        return newFlights.stream().map(this::saveFlight).toList();
    }

    Optional<FlightAvailability> findById(long flightId);

    /** All flights departing within {@code [from, to)}, ordered by departure time. */
    List<FlightAvailability> findDepartingBetween(LocalDateTime from, LocalDateTime to);

    /** Flights on one route departing within {@code [from, to)}, ordered by departure time. */
    List<FlightAvailability> findByRoute(String originCode, String destinationCode,
                                         LocalDateTime from, LocalDateTime to);

    /** Airports that have at least one flight from {@code originCode} departing after {@code after}. */
    List<Airport> findDestinationsFrom(String originCode, LocalDateTime after);

    /** Airports that have at least one flight departing after {@code after}. */
    List<Airport> findOriginsWithFlightsAfter(LocalDateTime after);

    boolean isEmpty();
}
