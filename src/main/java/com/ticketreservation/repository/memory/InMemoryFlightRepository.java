package com.ticketreservation.repository.memory;

import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.NewFlight;
import com.ticketreservation.repository.FlightRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class InMemoryFlightRepository implements FlightRepository {

    private static final Comparator<FlightAvailability> BY_DEPARTURE =
            Comparator.comparing((FlightAvailability f) -> f.flight().departure())
                    .thenComparing(f -> f.flight().flightNumber());

    private final InMemoryStore store;

    InMemoryFlightRepository(InMemoryStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public Airport saveAirport(Airport airport) {
        return store.locked(() -> {
            store.airports.put(airport.code(), airport);
            return airport;
        });
    }

    @Override
    public List<Airport> findAllAirports() {
        return store.locked(() -> List.copyOf(store.airports.values()));
    }

    @Override
    public Optional<Airport> findAirport(String code) {
        return store.locked(() -> Optional.ofNullable(store.airports.get(code)));
    }

    @Override
    public Flight saveFlight(NewFlight newFlight) {
        return store.locked(() -> {
            requireKnownAirport(newFlight.origin());
            requireKnownAirport(newFlight.destination());
            Flight flight = newFlight.withId(store.nextFlightId());
            store.flights.put(flight.id(), flight);
            store.availableSeats.put(flight.id(), flight.aircraft().capacity());
            return flight;
        });
    }

    @Override
    public Optional<FlightAvailability> findById(long flightId) {
        return store.locked(() -> Optional.ofNullable(store.flights.get(flightId)).map(this::withSeats));
    }

    @Override
    public List<FlightAvailability> findDepartingBetween(LocalDateTime from, LocalDateTime to) {
        return query(f -> departsWithin(f, from, to));
    }

    @Override
    public List<FlightAvailability> findByRoute(String originCode, String destinationCode,
                                                LocalDateTime from, LocalDateTime to) {
        return query(f -> f.origin().code().equals(originCode)
                && f.destination().code().equals(destinationCode)
                && departsWithin(f, from, to));
    }

    @Override
    public List<Airport> findDestinationsFrom(String originCode, LocalDateTime after) {
        return store.locked(() -> store.flights.values().stream()
                .filter(f -> f.origin().code().equals(originCode) && f.departure().isAfter(after))
                .map(Flight::destination)
                .distinct()
                .sorted(Comparator.comparing(Airport::code))
                .toList());
    }

    @Override
    public List<Airport> findOriginsWithFlightsAfter(LocalDateTime after) {
        return store.locked(() -> store.flights.values().stream()
                .filter(f -> f.departure().isAfter(after))
                .map(Flight::origin)
                .distinct()
                .sorted(Comparator.comparing(Airport::code))
                .toList());
    }

    @Override
    public boolean isEmpty() {
        return store.locked(store.flights::isEmpty);
    }

    private List<FlightAvailability> query(Predicate<Flight> filter) {
        return store.locked(() -> store.flights.values().stream()
                .filter(filter)
                .map(this::withSeats)
                .sorted(BY_DEPARTURE)
                .toList());
    }

    private FlightAvailability withSeats(Flight flight) {
        return new FlightAvailability(flight, store.availableSeats.get(flight.id()));
    }

    private static boolean departsWithin(Flight flight, LocalDateTime from, LocalDateTime to) {
        return !flight.departure().isBefore(from) && flight.departure().isBefore(to);
    }

    private void requireKnownAirport(Airport airport) {
        if (!store.airports.containsKey(airport.code())) {
            throw new IllegalArgumentException("Unknown airport: " + airport.code());
        }
    }
}
