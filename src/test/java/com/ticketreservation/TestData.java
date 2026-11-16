package com.ticketreservation;

import com.ticketreservation.domain.Aircraft;
import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Money;
import com.ticketreservation.domain.NewFlight;
import com.ticketreservation.domain.Passenger;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class TestData {

    /** "Now" in every test: 1 March 2030, 10:00. */
    public static final LocalDateTime NOW = LocalDateTime.of(2030, 3, 1, 10, 0);

    public static final Airport TIRANA = new Airport("TIA", "Tirana International Airport", "Tirana", "Albania");
    public static final Airport FRANKFURT = new Airport("FRA", "Frankfurt Airport", "Frankfurt", "Germany");
    public static final Airport MUNICH = new Airport("MUC", "Munich Airport", "Munich", "Germany");

    private TestData() {
    }

    public static MutableClock clock() {
        return new MutableClock(NOW.toInstant(ZoneOffset.UTC));
    }

    public static NewFlight flight(String number, Airport from, Airport to, LocalDateTime departure) {
        return flight(number, from, to, departure, Aircraft.BOEING_737, "80.00", "200.00");
    }

    public static NewFlight flight(String number, Airport from, Airport to, LocalDateTime departure,
                                   Aircraft aircraft, String economy, String first) {
        return new NewFlight(number, from, to, departure, departure.plusHours(2), aircraft,
                Money.euros(economy), Money.euros(first));
    }

    public static Passenger passenger(String firstName) {
        return new Passenger(firstName, "Tester", "+355691234567");
    }

    public static List<Passenger> passengers(int count) {
        List<Passenger> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(passenger("Passenger" + lettersFor(i)));
        }
        return list;
    }

    /** 0 -> "A", 25 -> "Z", 26 -> "AA" ... so generated names contain letters only. */
    private static String lettersFor(int index) {
        StringBuilder sb = new StringBuilder();
        for (int n = index; n >= 0; n = n / 26 - 1) {
            sb.insert(0, (char) ('A' + n % 26));
        }
        return sb.toString();
    }

    /** A clock that tests can move forward, always in UTC. */
    public static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        public void set(LocalDateTime time) {
            this.instant = time.toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
