package com.ticketreservation.service;

import com.ticketreservation.domain.Aircraft;
import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Money;
import com.ticketreservation.domain.NewFlight;
import com.ticketreservation.repository.FlightRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fills an empty schedule with demo airports and flights, starting tomorrow, so the
 * application is usable straight after a fresh install. The data is deterministic: the same
 * date always produces the same schedule and prices.
 */
public final class DemoDataSeeder {

    public static final int DAYS_AHEAD = 30;

    static final List<Airport> AIRPORTS = List.of(
            new Airport("TIA", "Tirana International Airport", "Tirana", "Albania"),
            new Airport("BRI", "Bari Karol Wojtyla Airport", "Bari", "Italy"),
            new Airport("BLQ", "Bologna Guglielmo Marconi Airport", "Bologna", "Italy"),
            new Airport("GOA", "Genoa Cristoforo Colombo Airport", "Genoa", "Italy"),
            new Airport("MXP", "Milan Malpensa Airport", "Milan", "Italy"),
            new Airport("PSA", "Pisa International Airport", "Pisa", "Italy"),
            new Airport("FRA", "Frankfurt Airport", "Frankfurt", "Germany"),
            new Airport("MUC", "Munich Airport", "Munich", "Germany"),
            new Airport("BVA", "Paris Beauvais Airport", "Paris", "France"),
            new Airport("XCR", "Paris Vatry Airport", "Paris", "France"));

    /**
     * Each route is flown in both directions. {@code daily = false} means Monday, Wednesday,
     * Friday and Sunday only.
     */
    private record Route(String from, String to, int minutes, Aircraft aircraft, int economy, int first,
                         int outboundHour, boolean daily) {
    }

    private static final List<Route> ROUTES = List.of(
            new Route("TIA", "BRI", 60, Aircraft.BOEING_737, 49, 99, 7, true),
            new Route("TIA", "BLQ", 95, Aircraft.AIRBUS_A320, 69, 139, 9, true),
            new Route("TIA", "GOA", 120, Aircraft.BOEING_737, 79, 159, 8, true),
            new Route("TIA", "MXP", 115, Aircraft.AIRBUS_A320, 75, 149, 13, true),
            new Route("TIA", "PSA", 105, Aircraft.BOEING_737, 65, 129, 16, true),
            new Route("TIA", "FRA", 135, Aircraft.BOEING_747, 99, 219, 6, true),
            new Route("TIA", "MUC", 120, Aircraft.AIRBUS_A320, 95, 199, 11, true),
            new Route("TIA", "BVA", 165, Aircraft.BOEING_737, 89, 189, 10, false),
            new Route("TIA", "XCR", 160, Aircraft.BOEING_737, 85, 179, 15, false),
            new Route("GOA", "FRA", 90, Aircraft.AIRBUS_A320, 89, 179, 7, false),
            new Route("GOA", "MUC", 85, Aircraft.AIRBUS_A320, 85, 175, 12, false),
            new Route("GOA", "BVA", 95, Aircraft.BOEING_737, 59, 129, 14, false),
            new Route("MXP", "FRA", 75, Aircraft.BOEING_747, 79, 169, 18, true),
            new Route("PSA", "MUC", 90, Aircraft.BOEING_737, 69, 149, 17, false),
            new Route("BLQ", "BVA", 100, Aircraft.BOEING_737, 55, 119, 19, false));

    private final FlightRepository flights;
    private final Clock clock;

    public DemoDataSeeder(FlightRepository flights, Clock clock) {
        this.flights = Objects.requireNonNull(flights);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Seeds demo data unless upcoming flights already exist.
     *
     * @return the number of flights created
     */
    public int seedIfEmpty() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!flights.findDepartingBetween(now, now.plusDays(DAYS_AHEAD)).isEmpty()) {
            return 0;
        }
        AIRPORTS.forEach(flights::saveAirport);
        return flights.saveFlights(buildSchedule(LocalDate.now(clock).plusDays(1), DAYS_AHEAD)).size();
    }

    static List<NewFlight> buildSchedule(LocalDate firstDay, int days) {
        Map<String, Airport> byCode = AIRPORTS.stream().collect(Collectors.toMap(Airport::code, Function.identity()));
        List<NewFlight> schedule = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            LocalDate date = firstDay.plusDays(day);
            for (int r = 0; r < ROUTES.size(); r++) {
                Route route = ROUTES.get(r);
                if (!route.daily() && !isFourTimesWeeklyDay(date.getDayOfWeek())) {
                    continue;
                }
                Airport a = byCode.get(route.from());
                Airport b = byCode.get(route.to());
                // Outbound in the morning/afternoon, return after a 1-hour turnaround.
                LocalDateTime out = date.atTime(LocalTime.of(route.outboundHour(), 0));
                LocalDateTime back = out.plusMinutes(route.minutes() + 60L);
                schedule.add(flight("TR" + (100 + r * 2), a, b, out, route, date));
                schedule.add(flight("TR" + (101 + r * 2), b, a, back, route, date));
            }
        }
        return schedule;
    }

    private static NewFlight flight(String number, Airport from, Airport to, LocalDateTime departure,
                                    Route route, LocalDate date) {
        int surcharge = demandSurcharge(date);
        return new NewFlight(number, from, to, departure, departure.plusMinutes(route.minutes()), route.aircraft(),
                Money.euros(route.economy() + surcharge), Money.euros(route.first() + surcharge * 2L));
    }

    /** Fridays and Sundays are busier, so fares are higher. */
    private static int demandSurcharge(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case FRIDAY, SUNDAY -> 20;
            case SATURDAY -> 10;
            default -> 0;
        };
    }

    private static boolean isFourTimesWeeklyDay(DayOfWeek day) {
        return day == DayOfWeek.MONDAY || day == DayOfWeek.WEDNESDAY
                || day == DayOfWeek.FRIDAY || day == DayOfWeek.SUNDAY;
    }
}
