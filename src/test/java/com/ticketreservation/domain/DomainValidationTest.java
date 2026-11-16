package com.ticketreservation.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.ticketreservation.TestData.FRANKFURT;
import static com.ticketreservation.TestData.TIRANA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainValidationTest {

    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2030, 5, 1, 8, 0);

    @Test
    void flightMustArriveAfterItDeparts() {
        assertThrows(IllegalArgumentException.class, () -> new NewFlight("TR1", TIRANA, FRANKFURT,
                DEPARTURE, DEPARTURE.minusHours(1), Aircraft.BOEING_737, Money.euros(50), Money.euros(90)));
        assertThrows(IllegalArgumentException.class, () -> new NewFlight("TR1", TIRANA, FRANKFURT,
                DEPARTURE, DEPARTURE, Aircraft.BOEING_737, Money.euros(50), Money.euros(90)));
    }

    @Test
    void flightMustConnectTwoDifferentAirports() {
        assertThrows(IllegalArgumentException.class, () -> new NewFlight("TR1", TIRANA, TIRANA,
                DEPARTURE, DEPARTURE.plusHours(1), Aircraft.BOEING_737, Money.euros(50), Money.euros(90)));
    }

    @Test
    void flightKnowsPriceDurationAndReverseRoute() {
        Flight out = new NewFlight("TR1", TIRANA, FRANKFURT, DEPARTURE, DEPARTURE.plusMinutes(135),
                Aircraft.BOEING_737, Money.euros(50), Money.euros(90)).withId(1);
        Flight back = new NewFlight("TR2", FRANKFURT, TIRANA, DEPARTURE.plusDays(2), DEPARTURE.plusDays(2).plusHours(2),
                Aircraft.BOEING_737, Money.euros(55), Money.euros(95)).withId(2);

        assertEquals(Money.euros(50), out.priceFor(FareClass.ECONOMY));
        assertEquals(Money.euros(90), out.priceFor(FareClass.FIRST));
        assertEquals(135L, out.duration().toMinutes());
        assertTrue(back.isReverseRouteOf(out));
        assertFalse(out.isReverseRouteOf(out));
    }

    @Test
    void airportCodeMustBeThreeUpperCaseLetters() {
        assertThrows(IllegalArgumentException.class, () -> new Airport("tia", "x", "y", "z"));
        assertThrows(IllegalArgumentException.class, () -> new Airport("TIRA", "x", "y", "z"));
        assertEquals("TIA - Tirana, Albania", TIRANA.label());
    }

    @Test
    void passengerAcceptsInternationalNames() {
        Passenger p = new Passenger("  Anne-Marie ", "O'Neil", "+355 69 123-4567");
        assertEquals("Anne-Marie O'Neil", p.fullName());
        assertEquals("+355691234567", p.phoneNumber());
        assertTrue(Passenger.isValidName("Ëndrit"));
        assertTrue(Passenger.isValidName("José María"));
    }

    @Test
    void passengerRejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new Passenger("", "Doe", "0691234567"));
        assertThrows(IllegalArgumentException.class, () -> new Passenger("J0hn", "Doe", "0691234567"));
        assertThrows(IllegalArgumentException.class, () -> new Passenger("John", "Doe", "12345"));
        assertThrows(IllegalArgumentException.class, () -> new Passenger("John", "Doe", "phone-number"));
        assertFalse(Passenger.isValidPhone(null));
    }

    @Test
    void userToStringDoesNotLeakPasswordHash() {
        User user = new User(1, "a@b.com", "pbkdf2-sha256$secret");
        assertFalse(user.toString().contains("secret"));
    }
}
