package com.ticketreservation.service;

import com.ticketreservation.TestData;
import com.ticketreservation.domain.NewFlight;
import com.ticketreservation.repository.FlightRepository;
import com.ticketreservation.repository.memory.InMemoryRepositories;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.ticketreservation.TestData.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDataSeederTest {

    @Test
    void seedsOnceAndOnlyFutureFlights() {
        FlightRepository flights = InMemoryRepositories.create().flights();
        DemoDataSeeder seeder = new DemoDataSeeder(flights, TestData.clock());

        int created = seeder.seedIfEmpty();

        assertTrue(created > 0);
        assertEquals(DemoDataSeeder.AIRPORTS.size(), flights.findAllAirports().size());
        assertEquals(0, seeder.seedIfEmpty(), "second run does nothing");
        assertTrue(flights.findDepartingBetween(NOW.minusYears(1), NOW).isEmpty(), "no flights in the past");
    }

    @Test
    void everyRouteIsServedInBothDirections() {
        List<NewFlight> schedule = DemoDataSeeder.buildSchedule(LocalDate.of(2030, 3, 4), 7);
        for (NewFlight f : schedule) {
            assertTrue(schedule.stream().anyMatch(r ->
                            r.origin().equals(f.destination()) && r.destination().equals(f.origin())),
                    "no return service for " + f.flightNumber());
        }
    }

    @Test
    void scheduleIsDeterministic() {
        LocalDate start = LocalDate.of(2030, 3, 4);
        assertEquals(DemoDataSeeder.buildSchedule(start, 14), DemoDataSeeder.buildSchedule(start, 14));
    }
}
