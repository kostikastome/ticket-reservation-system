package com.ticketreservation.service;

import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.Money;
import com.ticketreservation.exception.NotFoundException;
import com.ticketreservation.exception.ValidationException;
import com.ticketreservation.repository.FlightRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Read-only queries over the flight schedule. Only flights that have not departed yet are
 * returned, and searches for booking also respect the booking cut-off. Full flights are still
 * listed (with 0 seats) so the customer can see them.
 */
public final class FlightSearchService {

    /** How far ahead the schedule is shown when no date is given. */
    public static final int DEFAULT_HORIZON_DAYS = 90;

    private final FlightRepository flights;
    private final Clock clock;

    public FlightSearchService(FlightRepository flights, Clock clock) {
        this.flights = Objects.requireNonNull(flights);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<Airport> originAirports() {
        return flights.findOriginsWithFlightsAfter(now());
    }

    public List<Airport> destinationsFrom(String originCode) {
        return flights.findDestinationsFrom(originCode, now());
    }

    /** Dates on which the route has at least one future flight with a free seat. */
    public List<LocalDate> availableDates(String originCode, String destinationCode) {
        return List.copyOf(lowestFaresByDate(originCode, destinationCode).keySet());
    }

    /** For each date with a bookable flight on the route, the cheapest economy fare that day. */
    public SortedMap<LocalDate, Money> lowestFaresByDate(String originCode, String destinationCode) {
        LocalDateTime from = bookableAfter();
        SortedMap<LocalDate, Money> fares = new TreeMap<>();
        for (FlightAvailability f : flights.findByRoute(originCode, destinationCode, from,
                from.plusDays(DEFAULT_HORIZON_DAYS))) {
            if (f.availableSeats() > 0) {
                fares.merge(f.flight().departure().toLocalDate(), f.flight().economyPrice(),
                        (a, b) -> a.compareTo(b) <= 0 ? a : b);
            }
        }
        return fares;
    }

    /** A route's cheapest bookable flight, used for "deals" on the home page. */
    public record Deal(Flight flight) {
    }

    /**
     * The cheapest bookable flight on each route, cheapest routes first, at most {@code limit}
     * of them. Only one deal per destination is returned so the list stays varied.
     */
    public List<Deal> cheapestDeals(int limit) {
        LocalDateTime from = bookableAfter();
        Map<String, Flight> cheapestPerDestination = new HashMap<>();
        for (FlightAvailability fa : flights.findDepartingBetween(from, from.plusDays(DEFAULT_HORIZON_DAYS))) {
            if (fa.availableSeats() == 0) {
                continue;
            }
            Flight f = fa.flight();
            cheapestPerDestination.merge(f.destination().code(), f,
                    (a, b) -> b.economyPrice().compareTo(a.economyPrice()) < 0 ? b : a);
        }
        return cheapestPerDestination.values().stream()
                .sorted(Comparator.comparing(Flight::economyPrice).thenComparing(Flight::departure))
                .limit(limit)
                .map(Deal::new)
                .toList();
    }

    /** Flights on the route departing on {@code date} that are still open for booking. */
    public List<FlightAvailability> search(String originCode, String destinationCode, LocalDate date) {
        Objects.requireNonNull(date, "date");
        if (originCode.equals(destinationCode)) {
            throw new ValidationException("Origin and destination must be different airports");
        }
        LocalDateTime earliest = bookableAfter();
        LocalDateTime from = date.atStartOfDay();
        if (from.isBefore(earliest)) {
            from = earliest;
        }
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        if (!from.isBefore(to)) {
            return List.of();
        }
        return flights.findByRoute(originCode, destinationCode, from, to);
    }

    /**
     * Return flights for a round trip: the reverse route, departing after the outbound flight
     * lands, within the search horizon.
     */
    public List<FlightAvailability> returnFlightsFor(Flight outbound) {
        LocalDateTime earliest = outbound.arrival();
        return flights.findByRoute(outbound.destination().code(), outbound.origin().code(),
                earliest, earliest.plusDays(DEFAULT_HORIZON_DAYS)).stream()
                .filter(f -> f.flight().departure().isAfter(earliest))
                .toList();
    }

    /** All upcoming flights, optionally filtered by free text (flight number, airport code, city or country). */
    public List<FlightAvailability> upcomingFlights(String filter) {
        LocalDateTime now = now();
        List<FlightAvailability> upcoming = flights.findDepartingBetween(now, now.plusDays(DEFAULT_HORIZON_DAYS));
        if (filter == null || filter.isBlank()) {
            return upcoming;
        }
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        return upcoming.stream().filter(f -> matches(f.flight(), needle)).toList();
    }

    public FlightAvailability getFlight(long flightId) {
        return flights.findById(flightId)
                .orElseThrow(() -> new NotFoundException("Flight " + flightId + " does not exist"));
    }

    private static boolean matches(Flight f, String needle) {
        return f.flightNumber().toLowerCase(Locale.ROOT).contains(needle)
                || airportMatches(f.origin(), needle)
                || airportMatches(f.destination(), needle);
    }

    private static boolean airportMatches(Airport a, String needle) {
        return a.code().toLowerCase(Locale.ROOT).contains(needle)
                || a.city().toLowerCase(Locale.ROOT).contains(needle)
                || a.country().toLowerCase(Locale.ROOT).contains(needle)
                || a.name().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** Flights departing before this moment are past the online booking cut-off. */
    private LocalDateTime bookableAfter() {
        return now().plus(BookingService.BOOKING_CUTOFF);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
