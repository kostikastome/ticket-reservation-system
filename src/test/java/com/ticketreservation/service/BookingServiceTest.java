package com.ticketreservation.service;

import com.ticketreservation.TestData;
import com.ticketreservation.TestData.MutableClock;
import com.ticketreservation.domain.Aircraft;
import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.BookingStatus;
import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.Money;
import com.ticketreservation.domain.User;
import com.ticketreservation.exception.NotFoundException;
import com.ticketreservation.exception.SeatsUnavailableException;
import com.ticketreservation.exception.ValidationException;
import com.ticketreservation.repository.Repositories;
import com.ticketreservation.repository.memory.InMemoryRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.SplittableRandom;

import static com.ticketreservation.TestData.FRANKFURT;
import static com.ticketreservation.TestData.MUNICH;
import static com.ticketreservation.TestData.NOW;
import static com.ticketreservation.TestData.TIRANA;
import static com.ticketreservation.TestData.flight;
import static com.ticketreservation.TestData.passengers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingServiceTest {

    private static final LocalDateTime OUT_DEPARTURE = NOW.plusDays(5);

    private Repositories repos;
    private MutableClock clock;
    private BookingService service;
    private User alice;
    private User bob;
    private Flight outbound;
    private Flight inbound;

    @BeforeEach
    void setUp() {
        repos = InMemoryRepositories.create();
        clock = TestData.clock();
        service = new BookingService(repos.flights(), repos.bookings(), new FareCalculator(),
                new BookingReferenceGenerator(new SplittableRandom(42)), clock);
        List.of(TIRANA, FRANKFURT, MUNICH).forEach(repos.flights()::saveAirport);
        outbound = repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, OUT_DEPARTURE));
        inbound = repos.flights().saveFlight(flight("TR2", FRANKFURT, TIRANA, OUT_DEPARTURE.plusDays(3)));
        alice = repos.users().create("alice@example.com", "hash");
        bob = repos.users().create("bob@example.com", "hash");
    }

    private int seatsLeft(Flight flight) {
        return repos.flights().findById(flight.id()).orElseThrow().availableSeats();
    }

    @Test
    void oneWayBookingReservesSeatsAndChargesTheQuotedPrice() {
        BookingRequest request = BookingRequest.oneWay(outbound.id(), FareClass.ECONOMY, true, passengers(2));

        PriceQuote quote = service.quote(request);
        Booking booking = service.book(alice, request);

        assertEquals(Money.euros("190.00"), quote.total());
        assertEquals(quote.total(), booking.totalPrice());
        assertEquals(BookingStatus.CONFIRMED, booking.status());
        assertEquals(6, booking.reference().length());
        assertEquals(Aircraft.BOEING_737.capacity() - 2, seatsLeft(outbound));
        assertEquals(Aircraft.BOEING_737.capacity(), seatsLeft(inbound));
    }

    @Test
    void roundTripReservesSeatsOnBothFlights() {
        Booking booking = service.book(alice,
                BookingRequest.roundTrip(outbound.id(), inbound.id(), FareClass.FIRST, false, passengers(3)));

        assertTrue(booking.isRoundTrip());
        assertEquals(Money.euros("1200.00"), booking.totalPrice());
        assertEquals(Aircraft.BOEING_737.capacity() - 3, seatsLeft(outbound));
        assertEquals(Aircraft.BOEING_737.capacity() - 3, seatsLeft(inbound));
    }

    @Test
    void returnFlightMustFlyTheReverseRoute() {
        Flight toMunich = repos.flights().saveFlight(flight("TR3", FRANKFURT, MUNICH, OUT_DEPARTURE.plusDays(1)));
        assertThrows(ValidationException.class, () -> service.book(alice,
                BookingRequest.roundTrip(outbound.id(), toMunich.id(), FareClass.ECONOMY, false, passengers(1))));
    }

    @Test
    void returnFlightMustLeaveAfterTheOutboundLands() {
        Flight tooEarly = repos.flights().saveFlight(
                flight("TR4", FRANKFURT, TIRANA, OUT_DEPARTURE.plusHours(1)));
        assertThrows(ValidationException.class, () -> service.book(alice,
                BookingRequest.roundTrip(outbound.id(), tooEarly.id(), FareClass.ECONOMY, false, passengers(1))));
        assertEquals(Aircraft.BOEING_737.capacity(), seatsLeft(outbound), "nothing reserved on failure");
    }

    @Test
    void cannotBookMoreSeatsThanAreLeft() {
        Flight small = repos.flights().saveFlight(flight("TR5", TIRANA, MUNICH, OUT_DEPARTURE));
        for (int i = 0; i < Aircraft.BOEING_737.capacity() / 9; i++) {
            service.book(bob, BookingRequest.oneWay(small.id(), FareClass.ECONOMY, false, passengers(9)));
        }
        int left = seatsLeft(small);
        assertEquals(Aircraft.BOEING_737.capacity() % 9, left);

        assertThrows(SeatsUnavailableException.class, () -> service.book(alice,
                BookingRequest.oneWay(small.id(), FareClass.ECONOMY, false, passengers(left + 1))));
        assertEquals(left, seatsLeft(small));
    }

    @Test
    void enforcesPassengerLimits() {
        assertThrows(ValidationException.class, () -> service.book(alice,
                BookingRequest.oneWay(outbound.id(), FareClass.ECONOMY, false, List.of())));
        assertThrows(ValidationException.class, () -> service.book(alice,
                BookingRequest.oneWay(outbound.id(), FareClass.ECONOMY, false, passengers(10))));
    }

    @Test
    void cannotBookUnknownOrDepartedFlights() {
        assertThrows(NotFoundException.class, () -> service.book(alice,
                BookingRequest.oneWay(999, FareClass.ECONOMY, false, passengers(1))));

        clock.set(OUT_DEPARTURE.minusMinutes(30));
        assertThrows(ValidationException.class, () -> service.book(alice,
                BookingRequest.oneWay(outbound.id(), FareClass.ECONOMY, false, passengers(1))));
    }

    @Test
    void cancellingGivesSeatsBack() {
        Booking booking = service.book(alice,
                BookingRequest.roundTrip(outbound.id(), inbound.id(), FareClass.ECONOMY, false, passengers(2)));

        Booking cancelled = service.cancel(alice, booking.reference().toLowerCase());

        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertEquals(Aircraft.BOEING_737.capacity(), seatsLeft(outbound));
        assertEquals(Aircraft.BOEING_737.capacity(), seatsLeft(inbound));
        assertThrows(ValidationException.class, () -> service.cancel(alice, booking.reference()));
        assertEquals(Aircraft.BOEING_737.capacity(), seatsLeft(outbound), "seats are not released twice");
    }

    @Test
    void cannotCancelWithin24HoursOfDeparture() {
        Booking booking = service.book(alice,
                BookingRequest.oneWay(outbound.id(), FareClass.ECONOMY, false, passengers(1)));

        clock.set(OUT_DEPARTURE.minusHours(23));

        assertThrows(ValidationException.class, () -> service.cancel(alice, booking.reference()));
        assertEquals(BookingStatus.CONFIRMED, service.findBooking(alice, booking.reference()).status());
    }

    @Test
    void usersCannotSeeOrCancelOtherUsersBookings() {
        Booking booking = service.book(alice,
                BookingRequest.oneWay(outbound.id(), FareClass.ECONOMY, false, passengers(1)));

        assertThrows(NotFoundException.class, () -> service.findBooking(bob, booking.reference()));
        assertThrows(NotFoundException.class, () -> service.cancel(bob, booking.reference()));
        assertTrue(service.bookingsOf(bob).isEmpty());
    }

    @Test
    void listsUsersBookingsNewestFirst() {
        Booking first = service.book(alice,
                BookingRequest.oneWay(outbound.id(), FareClass.ECONOMY, false, passengers(1)));
        clock.set(NOW.plusHours(1));
        Booking second = service.book(alice,
                BookingRequest.oneWay(inbound.id(), FareClass.ECONOMY, false, passengers(1)));

        List<Booking> bookings = service.bookingsOf(alice);

        assertEquals(List.of(second.reference(), first.reference()),
                bookings.stream().map(Booking::reference).toList());
        assertFalse(first.reference().equals(second.reference()));
    }
}
