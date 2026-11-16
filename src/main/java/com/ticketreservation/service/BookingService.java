package com.ticketreservation.service;

import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.BookingStatus;
import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.NewBooking;
import com.ticketreservation.domain.User;
import com.ticketreservation.exception.NotFoundException;
import com.ticketreservation.exception.SeatsUnavailableException;
import com.ticketreservation.exception.ValidationException;
import com.ticketreservation.repository.BookingRepository;
import com.ticketreservation.repository.FlightRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Quotes, creates and cancels bookings, and enforces the business rules around them.
 */
public final class BookingService {

    public static final int MAX_PASSENGERS_PER_BOOKING = 9;
    /** Bookings can be cancelled up to this long before the first flight departs. */
    public static final Duration CANCELLATION_CUTOFF = Duration.ofHours(24);
    /** Online booking closes this long before departure. */
    public static final Duration BOOKING_CUTOFF = Duration.ofMinutes(45);
    private static final int MAX_REFERENCE_ATTEMPTS = 5;

    private final FlightRepository flights;
    private final BookingRepository bookings;
    private final FareCalculator fareCalculator;
    private final BookingReferenceGenerator references;
    private final Clock clock;

    public BookingService(FlightRepository flights, BookingRepository bookings, FareCalculator fareCalculator,
                          BookingReferenceGenerator references, Clock clock) {
        this.flights = Objects.requireNonNull(flights);
        this.bookings = Objects.requireNonNull(bookings);
        this.fareCalculator = Objects.requireNonNull(fareCalculator);
        this.references = Objects.requireNonNull(references);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Validates the request and prices it without reserving anything. */
    public PriceQuote quote(BookingRequest request) {
        return quote(request.outboundFlightId(), request.returnFlightId(), request.fareClass(),
                request.passengers().size(), request.checkedBaggage());
    }

    /** Prices a trip before passenger details are known, e.g. for a live price in the UI. */
    public PriceQuote quote(long outboundFlightId, OptionalLong returnFlightId, FareClass fareClass,
                            int passengerCount, boolean checkedBaggage) {
        Objects.requireNonNull(fareClass, "fareClass");
        return fareCalculator.quote(validatedFlights(outboundFlightId, returnFlightId, passengerCount), fareClass,
                passengerCount, checkedBaggage);
    }

    /**
     * Books the trip. Seats on every flight are reserved atomically with the booking.
     *
     * @throws ValidationException       if the request breaks a booking rule
     * @throws SeatsUnavailableException if another customer took the last seats first
     */
    public Booking book(User user, BookingRequest request) {
        Objects.requireNonNull(user, "user");
        List<Flight> trip = validatedFlights(request.outboundFlightId(), request.returnFlightId(),
                request.passengers().size());
        PriceQuote quote = fareCalculator.quote(trip, request.fareClass(), request.passengers().size(),
                request.checkedBaggage());

        NewBooking newBooking = new NewBooking(
                uniqueReference(),
                user.id(),
                trip.get(0),
                trip.size() > 1 ? trip.get(1) : null,
                request.fareClass(),
                request.checkedBaggage(),
                request.passengers(),
                quote.total(),
                clock.instant());
        return bookings.create(newBooking);
    }

    /** The user's bookings, newest first. */
    public List<Booking> bookingsOf(User user) {
        return bookings.findByUserId(user.id());
    }

    /**
     * Finds one of the user's bookings. Another user's booking is reported as not found, so
     * references cannot be used to discover other customers' trips.
     */
    public Booking findBooking(User user, String reference) {
        String normalized = reference == null ? "" : reference.trim().toUpperCase(Locale.ROOT);
        return bookings.findByReference(normalized)
                .filter(b -> b.userId() == user.id())
                .orElseThrow(() -> new NotFoundException("No booking found with reference " + normalized));
    }

    public Booking cancel(User user, String reference) {
        Booking booking = findBooking(user, reference);
        if (booking.status() == BookingStatus.CANCELLED) {
            throw new ValidationException("Booking " + booking.reference() + " is already cancelled");
        }
        if (!isCancellable(booking)) {
            throw new ValidationException("Bookings can only be cancelled more than "
                    + CANCELLATION_CUTOFF.toHours() + " hours before departure");
        }
        return bookings.cancel(booking.id())
                .orElseThrow(() -> new ValidationException("Booking " + booking.reference() + " is already cancelled"));
    }

    /** True if the booking is confirmed and its first flight departs more than 24 hours from now. */
    public boolean isCancellable(Booking booking) {
        LocalDateTime deadline = booking.outboundFlight().departure().minus(CANCELLATION_CUTOFF);
        return booking.status() == BookingStatus.CONFIRMED && now().isBefore(deadline);
    }

    private List<Flight> validatedFlights(long outboundFlightId, OptionalLong returnFlightId, int passengers) {
        if (passengers < 1) {
            throw new ValidationException("A booking needs at least one passenger");
        }
        if (passengers > MAX_PASSENGERS_PER_BOOKING) {
            throw new ValidationException("A booking can have at most " + MAX_PASSENGERS_PER_BOOKING + " passengers");
        }

        List<Flight> trip = new ArrayList<>(2);
        Flight outbound = bookableFlight(outboundFlightId, passengers);
        trip.add(outbound);

        if (returnFlightId.isPresent()) {
            Flight inbound = bookableFlight(returnFlightId.getAsLong(), passengers);
            if (!inbound.isReverseRouteOf(outbound)) {
                throw new ValidationException("The return flight must fly from " + outbound.destination().code()
                        + " back to " + outbound.origin().code());
            }
            if (!inbound.departure().isAfter(outbound.arrival())) {
                throw new ValidationException("The return flight must depart after the outbound flight lands");
            }
            trip.add(inbound);
        }
        return trip;
    }

    private Flight bookableFlight(long flightId, int passengers) {
        FlightAvailability availability = flights.findById(flightId)
                .orElseThrow(() -> new NotFoundException("Flight " + flightId + " does not exist"));
        Flight flight = availability.flight();
        if (!now().isBefore(flight.departure().minus(BOOKING_CUTOFF))) {
            throw new ValidationException("Flight " + flight.flightNumber() + " is no longer open for booking");
        }
        // Early, friendly check. The repository re-checks atomically, which is what prevents overbooking.
        if (!availability.canSeat(passengers)) {
            throw new SeatsUnavailableException("Flight " + flight.flightNumber() + " has only "
                    + availability.availableSeats() + " seat(s) left");
        }
        return flight;
    }

    private String uniqueReference() {
        for (int attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt++) {
            String candidate = references.next();
            if (bookings.findByReference(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking reference");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
