package com.ticketreservation.repository;

import com.ticketreservation.TestData;
import com.ticketreservation.domain.Aircraft;
import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.BookingStatus;
import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.Money;
import com.ticketreservation.domain.NewBooking;
import com.ticketreservation.domain.Passenger;
import com.ticketreservation.domain.User;
import com.ticketreservation.exception.DuplicateEmailException;
import com.ticketreservation.exception.SeatsUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ticketreservation.TestData.FRANKFURT;
import static com.ticketreservation.TestData.MUNICH;
import static com.ticketreservation.TestData.NOW;
import static com.ticketreservation.TestData.TIRANA;
import static com.ticketreservation.TestData.flight;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour every storage implementation must have. Subclasses only say how to create the
 * repositories, so the in-memory and JDBC versions are held to exactly the same rules.
 */
public abstract class RepositoryContractTest {

    private static final LocalDateTime DAY = NOW.plusDays(10);

    protected Repositories repos;
    private User user;
    private int referenceCounter;

    protected abstract Repositories createRepositories() throws Exception;

    protected void tearDownStorage() throws Exception {
    }

    @BeforeEach
    void setUpRepositories() throws Exception {
        repos = createRepositories();
        List.of(TIRANA, FRANKFURT, MUNICH).forEach(repos.flights()::saveAirport);
        user = repos.users().create("owner@example.com", "hash");
    }

    @AfterEach
    void tearDownRepositories() throws Exception {
        tearDownStorage();
    }

    private NewBooking newBooking(Flight outbound, Flight inbound, int passengers) {
        return new NewBooking(String.format("REF%03d", ++referenceCounter), user.id(), outbound, inbound,
                FareClass.ECONOMY, true, TestData.passengers(passengers), Money.euros("123.45"),
                Instant.parse("2030-03-01T10:00:00.123Z").plus(referenceCounter, ChronoUnit.MINUTES));
    }

    private int seatsLeft(Flight flight) {
        return repos.flights().findById(flight.id()).orElseThrow().availableSeats();
    }


    @Test
    void storesAndReadsFlights() {
        Flight saved = repos.flights().saveFlight(
                flight("TR1", TIRANA, FRANKFURT, DAY, Aircraft.AIRBUS_A320, "79.99", "199.50"));

        FlightAvailability loaded = repos.flights().findById(saved.id()).orElseThrow();

        assertEquals(saved, loaded.flight());
        assertEquals(Aircraft.AIRBUS_A320.capacity(), loaded.availableSeats());
        assertTrue(repos.flights().findById(saved.id() + 1000).isEmpty());
        assertTrue(!repos.flights().isEmpty());
    }

    @Test
    void savingAnAirportTwiceUpdatesIt() {
        repos.flights().saveAirport(new Airport("TIA", "Mother Teresa Airport", "Tirana", "Albania"));

        assertEquals(3, repos.flights().findAllAirports().size());
        assertEquals("Mother Teresa Airport", repos.flights().findAirport("TIA").orElseThrow().name());
        assertTrue(repos.flights().findAirport("XXX").isEmpty());
    }

    @Test
    void findsFlightsByRouteAndTimeWindow() {
        Flight morning = repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, DAY.withHour(6)));
        Flight evening = repos.flights().saveFlight(flight("TR2", TIRANA, FRANKFURT, DAY.withHour(20)));
        repos.flights().saveFlight(flight("TR3", TIRANA, FRANKFURT, DAY.plusDays(1).withHour(6)));
        repos.flights().saveFlight(flight("TR4", TIRANA, MUNICH, DAY.withHour(7)));

        List<Flight> found = repos.flights()
                .findByRoute("TIA", "FRA", DAY.toLocalDate().atStartOfDay(), DAY.toLocalDate().plusDays(1).atStartOfDay())
                .stream().map(FlightAvailability::flight).toList();

        assertEquals(List.of(morning, evening), found);
        assertEquals(4, repos.flights().findDepartingBetween(NOW, NOW.plusYears(1)).size());
    }

    @Test
    void listsOriginsAndDestinationsWithFutureFlights() {
        repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, DAY));
        repos.flights().saveFlight(flight("TR2", TIRANA, MUNICH, DAY));
        repos.flights().saveFlight(flight("TR3", MUNICH, TIRANA, NOW.minusDays(1)));

        assertEquals(List.of("TIA"), repos.flights().findOriginsWithFlightsAfter(NOW).stream().map(Airport::code).toList());
        assertEquals(List.of("FRA", "MUC"),
                repos.flights().findDestinationsFrom("TIA", NOW).stream().map(Airport::code).toList());
    }


    @Test
    void storesUsersWithUniqueEmails() {
        User created = repos.users().create("alice@example.com", "pbkdf2$hash");

        assertEquals(created, repos.users().findByEmail("alice@example.com").orElseThrow());
        assertTrue(repos.users().findByEmail("nobody@example.com").isEmpty());
        assertThrows(DuplicateEmailException.class, () -> repos.users().create("alice@example.com", "other"));
    }


    @Test
    void createStoresTheBookingAndReservesSeatsOnEveryFlight() {
        Flight out = repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, DAY));
        Flight back = repos.flights().saveFlight(flight("TR2", FRANKFURT, TIRANA, DAY.plusDays(2)));

        Booking created = repos.bookings().create(newBooking(out, back, 3));
        Booking loaded = repos.bookings().findByReference(created.reference()).orElseThrow();

        assertEquals(created, loaded);
        assertEquals(BookingStatus.CONFIRMED, loaded.status());
        assertEquals(TestData.passengers(3), loaded.passengers());
        assertEquals(Aircraft.BOEING_737.capacity() - 3, seatsLeft(out));
        assertEquals(Aircraft.BOEING_737.capacity() - 3, seatsLeft(back));
    }

    @Test
    void oneWayBookingHasNoReturnFlight() {
        Flight out = repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, DAY));

        Booking loaded = repos.bookings().findByReference(repos.bookings().create(newBooking(out, null, 1)).reference())
                .orElseThrow();

        assertTrue(loaded.returnFlightOptional().isEmpty());
        assertEquals(List.of(out), loaded.flights());
    }

    @Test
    void createIsAllOrNothingWhenOneFlightIsFull() {
        Flight out = repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, DAY));
        Flight back = repos.flights().saveFlight(flight("TR2", FRANKFURT, TIRANA, DAY.plusDays(2)));
        int capacity = Aircraft.BOEING_737.capacity();
        repos.bookings().create(newBooking(back, null, capacity - 1));

        assertThrows(SeatsUnavailableException.class, () -> repos.bookings().create(newBooking(out, back, 2)));

        assertEquals(capacity, seatsLeft(out), "outbound seats must be rolled back");
        assertEquals(1, seatsLeft(back));
        assertEquals(1, repos.bookings().findByUserId(user.id()).size());
    }

    @Test
    void cancelReleasesSeatsExactlyOnce() {
        Flight out = repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, DAY));
        Flight back = repos.flights().saveFlight(flight("TR2", FRANKFURT, TIRANA, DAY.plusDays(2)));
        Booking booking = repos.bookings().create(newBooking(out, back, 4));

        Booking cancelled = repos.bookings().cancel(booking.id()).orElseThrow();

        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertEquals(BookingStatus.CANCELLED, repos.bookings().findByReference(booking.reference()).orElseThrow().status());
        assertEquals(Aircraft.BOEING_737.capacity(), seatsLeft(out));
        assertEquals(Aircraft.BOEING_737.capacity(), seatsLeft(back));

        assertTrue(repos.bookings().cancel(booking.id()).isEmpty());
        assertTrue(repos.bookings().cancel(9999).isEmpty());
        assertEquals(Aircraft.BOEING_737.capacity(), seatsLeft(out));
    }

    @Test
    void listsAUsersBookingsNewestFirst() {
        Flight out = repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, DAY));
        Booking first = repos.bookings().create(newBooking(out, null, 1));
        Booking second = repos.bookings().create(newBooking(out, null, 1));
        User other = repos.users().create("other@example.com", "hash");

        assertEquals(List.of(second, first), repos.bookings().findByUserId(user.id()));
        assertTrue(repos.bookings().findByUserId(other.id()).isEmpty());
    }

    @Test
    void concurrentBookingsNeverOverbookAFlight() throws Exception {
        // An Airbus A320 has 180 seats. 60 customers each try to book 4 seats (240 in total)
        // at the same moment: exactly 45 must succeed and the flight must end up full, not negative.
        Flight flight = repos.flights().saveFlight(
                flight("TR9", TIRANA, MUNICH, DAY, Aircraft.AIRBUS_A320, "50", "100"));
        int customers = 60;
        int seatsEach = 4;
        List<NewBooking> requests = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            requests.add(newBooking(flight, null, seatsEach));
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (NewBooking request : requests) {
                Callable<Void> task = () -> {
                    start.await();
                    try {
                        repos.bookings().create(request);
                        succeeded.incrementAndGet();
                    } catch (SeatsUnavailableException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        int capacity = Aircraft.AIRBUS_A320.capacity();
        assertEquals(capacity / seatsEach, succeeded.get());
        assertEquals(customers - capacity / seatsEach, rejected.get());
        assertEquals(0, seatsLeft(flight));
        assertEquals(capacity / seatsEach, repos.bookings().findByUserId(user.id()).size());
    }
}
