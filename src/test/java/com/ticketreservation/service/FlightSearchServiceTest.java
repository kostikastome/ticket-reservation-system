package com.ticketreservation.service;

import com.ticketreservation.TestData;
import com.ticketreservation.domain.Aircraft;
import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.Money;
import com.ticketreservation.exception.NotFoundException;
import com.ticketreservation.exception.ValidationException;
import com.ticketreservation.repository.FlightRepository;
import com.ticketreservation.repository.memory.InMemoryRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.ticketreservation.TestData.FRANKFURT;
import static com.ticketreservation.TestData.MUNICH;
import static com.ticketreservation.TestData.NOW;
import static com.ticketreservation.TestData.TIRANA;
import static com.ticketreservation.TestData.flight;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightSearchServiceTest {

    private FlightRepository flights;
    private FlightSearchService search;

    @BeforeEach
    void setUp() {
        flights = InMemoryRepositories.create().flights();
        List.of(TIRANA, FRANKFURT, MUNICH).forEach(flights::saveAirport);
        search = new FlightSearchService(flights, TestData.clock());
    }

    @Test
    void hidesFlightsThatAlreadyDeparted() {
        flights.saveFlight(flight("OLD", TIRANA, FRANKFURT, NOW.minusHours(3)));
        Flight future = flights.saveFlight(flight("NEW", TIRANA, FRANKFURT, NOW.plusDays(1)));

        List<FlightAvailability> upcoming = search.upcomingFlights("");

        assertEquals(List.of(future), upcoming.stream().map(FlightAvailability::flight).toList());
    }

    @Test
    void searchReturnsOnlyTheRequestedRouteAndDayInDepartureOrder() {
        LocalDate day = NOW.toLocalDate().plusDays(2);
        Flight late = flights.saveFlight(flight("LATE", TIRANA, FRANKFURT, day.atTime(18, 0)));
        Flight early = flights.saveFlight(flight("EARLY", TIRANA, FRANKFURT, day.atTime(6, 0)));
        flights.saveFlight(flight("OTHER-DAY", TIRANA, FRANKFURT, day.plusDays(1).atTime(6, 0)));
        flights.saveFlight(flight("OTHER-ROUTE", TIRANA, MUNICH, day.atTime(7, 0)));

        List<Flight> found = search.search("TIA", "FRA", day).stream().map(FlightAvailability::flight).toList();

        assertEquals(List.of(early, late), found);
    }

    @Test
    void searchTodayRespectsTheBookingCutoff() {
        LocalDate today = NOW.toLocalDate();
        flights.saveFlight(flight("SOON", TIRANA, FRANKFURT, NOW.plusMinutes(30)));
        Flight later = flights.saveFlight(flight("LATER", TIRANA, FRANKFURT, NOW.plusHours(3)));

        assertEquals(List.of(later), search.search("TIA", "FRA", today).stream().map(FlightAvailability::flight).toList());
    }

    @Test
    void searchingTheSameAirportTwiceIsRejected() {
        assertThrows(ValidationException.class, () -> search.search("TIA", "TIA", NOW.toLocalDate()));
    }

    @Test
    void listsOriginsDestinationsAndDates() {
        flights.saveFlight(flight("A", TIRANA, FRANKFURT, NOW.plusDays(1)));
        flights.saveFlight(flight("B", TIRANA, MUNICH, NOW.plusDays(3)));
        flights.saveFlight(flight("C", TIRANA, MUNICH, NOW.plusDays(3).plusHours(2)));
        flights.saveFlight(flight("D", FRANKFURT, TIRANA, NOW.minusDays(1)));

        assertEquals(List.of("TIA"), search.originAirports().stream().map(Airport::code).toList());
        assertEquals(List.of("FRA", "MUC"), search.destinationsFrom("TIA").stream().map(Airport::code).toList());
        assertEquals(List.of(NOW.toLocalDate().plusDays(3)), search.availableDates("TIA", "MUC"));
    }

    @Test
    void reportsTheLowestFarePerDayAndTheCheapestDealPerDestination() {
        LocalDate day = NOW.toLocalDate().plusDays(2);
        flights.saveFlight(flight("A", TIRANA, MUNICH, day.atTime(8, 0), Aircraft.BOEING_737, "120", "200"));
        flights.saveFlight(flight("B", TIRANA, MUNICH, day.atTime(18, 0), Aircraft.BOEING_737, "60", "200"));
        flights.saveFlight(flight("C", TIRANA, MUNICH, day.plusDays(1).atTime(8, 0), Aircraft.BOEING_737, "90", "200"));
        flights.saveFlight(flight("D", TIRANA, FRANKFURT, day.atTime(9, 0), Aircraft.BOEING_737, "45", "100"));
        flights.saveFlight(flight("E", FRANKFURT, TIRANA, day.atTime(9, 0), Aircraft.BOEING_737, "70", "100"));

        assertEquals(Map.of(day, Money.euros(60), day.plusDays(1), Money.euros(90)),
                search.lowestFaresByDate("TIA", "MUC"));
        assertEquals(List.of(day, day.plusDays(1)), search.availableDates("TIA", "MUC"));
        assertEquals(List.of("D", "B", "E"), search.cheapestDeals(5).stream()
                .map(d -> d.flight().flightNumber()).toList());
        assertEquals(1, search.cheapestDeals(1).size());
    }

    @Test
    void returnFlightsDepartAfterTheOutboundLands() {
        Flight out = flights.saveFlight(flight("OUT", TIRANA, FRANKFURT, NOW.plusDays(1)));
        flights.saveFlight(flight("TOO-EARLY", FRANKFURT, TIRANA, NOW.plusDays(1).plusHours(1)));
        Flight ok = flights.saveFlight(flight("OK", FRANKFURT, TIRANA, NOW.plusDays(2)));
        flights.saveFlight(flight("WRONG-ROUTE", FRANKFURT, MUNICH, NOW.plusDays(2)));

        assertEquals(List.of(ok), search.returnFlightsFor(out).stream().map(FlightAvailability::flight).toList());
    }

    @Test
    void textFilterMatchesCityCountryCodeAndFlightNumber() {
        flights.saveFlight(flight("TR100", TIRANA, FRANKFURT, NOW.plusDays(1)));
        flights.saveFlight(flight("TR200", TIRANA, MUNICH, NOW.plusDays(1)));

        assertEquals(1, search.upcomingFlights("munich").size());
        assertEquals(2, search.upcomingFlights("germany").size());
        assertEquals(1, search.upcomingFlights("tr1").size());
        assertEquals(2, search.upcomingFlights(" TIA ").size());
        assertTrue(search.upcomingFlights("paris").isEmpty());
    }

    @Test
    void unknownFlightIsReported() {
        assertThrows(NotFoundException.class, () -> search.getFlight(42));
    }
}
