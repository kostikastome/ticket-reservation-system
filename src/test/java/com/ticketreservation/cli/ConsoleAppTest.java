package com.ticketreservation.cli;

import com.ticketreservation.TestData;
import com.ticketreservation.repository.Repositories;
import com.ticketreservation.repository.memory.InMemoryRepositories;
import com.ticketreservation.service.AuthService;
import com.ticketreservation.service.BookingReferenceGenerator;
import com.ticketreservation.service.BookingService;
import com.ticketreservation.service.FareCalculator;
import com.ticketreservation.service.FlightSearchService;
import com.ticketreservation.service.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

import static com.ticketreservation.TestData.FRANKFURT;
import static com.ticketreservation.TestData.NOW;
import static com.ticketreservation.TestData.TIRANA;
import static com.ticketreservation.TestData.flight;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the console UI with scripted input, end to end over the real services.
 */
class ConsoleAppTest {

    private Repositories repos;

    @BeforeEach
    void setUp() {
        repos = InMemoryRepositories.create();
        repos.flights().saveAirport(TIRANA);
        repos.flights().saveAirport(FRANKFURT);
        repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, NOW.plusDays(2).withHour(8)));
        repos.flights().saveFlight(flight("TR2", FRANKFURT, TIRANA, NOW.plusDays(5).withHour(8)));
    }

    private String run(String... inputLines) {
        TestData.MutableClock clock = TestData.clock();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Terminal terminal = new Terminal.StreamTerminal(
                new BufferedReader(new StringReader(String.join("\n", inputLines) + "\n")),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        new ConsoleApp(terminal,
                new AuthService(repos.users(), new PasswordHasher(1_000, new SecureRandom())),
                new FlightSearchService(repos.flights(), clock),
                new BookingService(repos.flights(), repos.bookings(), new FareCalculator(),
                        new BookingReferenceGenerator(), clock))
                .run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void registerBookRoundTripAndCancel() {
        String output = run(
                "2", "alice@example.com", "secret123", "secret123",   // create account
                "1",                                                  // book a flight
                "2", "1", "1", "1",                                   // from TIA (FRA is listed first), to FRA, date, flight
                "2", "1", "1",                                        // round trip, return date, return flight
                "1",                                                  // economy
                "2",                                                  // 2 passengers
                "John", "Doe", "+355691234567",
                "Jane", "Doe", "+355691234568",
                "y",                                                  // checked baggage
                "y",                                                  // confirm
                "0");                                                 // exit

        // 2 passengers x (80 fare + 15 bag) x 2 flights
        assertTrue(output.contains("Total: EUR 380.00"), output);
        assertTrue(output.contains("Booking confirmed!"), output);
        assertEquals(1, repos.bookings().findByUserId(1).size());

        String reference = repos.bookings().findByUserId(1).get(0).reference();
        String cancelOutput = run(
                "1", "alice@example.com", "secret123",                // log in
                "3", reference, "y",                                  // cancel
                "2", "",                                              // list bookings
                "0");
        assertTrue(cancelOutput.contains("Booking " + reference + " has been cancelled"), cancelOutput);
        assertTrue(cancelOutput.contains("CANCELLED"), cancelOutput);
    }

    @Test
    void invalidInputIsRejectedWithoutCrashing() {
        String output = run(
                "abc", "7",                                           // not a menu option
                "1", "nobody@example.com", "secret123",               // unknown user
                "2", "not-an-email", "secret123", "secret123",        // invalid email
                "0");

        assertTrue(output.contains("Please enter a number from 0 to 3."), output);
        assertTrue(output.contains("Error: Invalid email or password"), output);
        assertTrue(output.contains("Error: Please enter a valid email address"), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    @Test
    void decliningTheQuoteBooksNothing() {
        String output = run(
                "2", "bob@example.com", "secret123", "secret123",
                "1", "2", "1", "1", "1",                              // book: TIA -> FRA, date, flight
                "1",                                                  // one way
                "2", "1",                                             // first class, 1 passenger
                "Bob", "Smith", "0691234567",
                "n",                                                  // no baggage
                "n",                                                  // do not confirm
                "0");

        assertTrue(output.contains("Total: EUR 200.00"), output);
        assertTrue(output.contains("Nothing was charged"), output);
        assertTrue(repos.bookings().findByUserId(1).isEmpty());
    }

    @Test
    void endOfInputExitsCleanly() {
        String output = run("3", "");
        assertTrue(output.contains("TR1"), output);
        assertTrue(output.contains("Goodbye!"), output);
    }

    @Test
    void browsingShowsUpcomingFlights() {
        String output = run("3", "frankfurt", "0");
        assertTrue(output.contains("TR1"), output);
        assertTrue(output.contains("TR2"), output);
        assertTrue(output.contains("EUR 80.00"), output);
    }

    @Test
    void formatsDurations() {
        assertEquals("2h 05m", ConsoleApp.formatDuration(Duration.ofMinutes(125)));
        assertEquals(List.of("A  B", "-  -", "x  y"), new TextTable("A", "B").addRow("x", "y").render());
    }
}
