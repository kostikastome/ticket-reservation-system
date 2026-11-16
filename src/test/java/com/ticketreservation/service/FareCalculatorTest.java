package com.ticketreservation.service;

import com.ticketreservation.domain.Aircraft;
import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.ticketreservation.TestData.FRANKFURT;
import static com.ticketreservation.TestData.TIRANA;
import static com.ticketreservation.TestData.flight;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FareCalculatorTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2030, 5, 1, 8, 0);
    private final FareCalculator calculator = new FareCalculator();
    private final Flight outbound =
            flight("TR1", TIRANA, FRANKFURT, DAY, Aircraft.BOEING_737, "80.00", "200.00").withId(1);
    private final Flight inbound =
            flight("TR2", FRANKFURT, TIRANA, DAY.plusDays(3), Aircraft.BOEING_737, "95.50", "230.00").withId(2);

    @Test
    void oneWayEconomyWithoutBaggage() {
        PriceQuote quote = calculator.quote(List.of(outbound), FareClass.ECONOMY, 1, false);
        assertEquals(Money.euros("80.00"), quote.total());
        assertEquals(1, quote.lines().size());
    }

    @Test
    void multipliesFareAndBaggageByPassengers() {
        PriceQuote quote = calculator.quote(List.of(outbound), FareClass.ECONOMY, 3, true);
        // 3 x 80 fare + 3 x 15 baggage
        assertEquals(Money.euros("285.00"), quote.total());
        assertEquals(2, quote.lines().size());
    }

    @Test
    void firstClassUsesFirstClassFareAndBaggageFee() {
        PriceQuote quote = calculator.quote(List.of(outbound), FareClass.FIRST, 2, true);
        // 2 x 200 fare + 2 x 25 baggage
        assertEquals(Money.euros("450.00"), quote.total());
    }

    @Test
    void roundTripUsesTheReturnFlightsOwnFare() {
        PriceQuote quote = calculator.quote(List.of(outbound, inbound), FareClass.ECONOMY, 2, true);
        // outbound 2 x (80 + 15) + return 2 x (95.50 + 15)
        assertEquals(Money.euros("411.00"), quote.total());
        assertEquals(4, quote.lines().size());
    }

    @Test
    void linesAddUpToTheTotal() {
        PriceQuote quote = calculator.quote(List.of(outbound, inbound), FareClass.FIRST, 4, true);
        Money sum = quote.lines().stream().map(PriceQuote.Line::amount).reduce(Money.euros(0), Money::plus);
        assertEquals(quote.total(), sum);
    }

    @Test
    void rejectsEmptyTripsAndPassengerLists() {
        assertThrows(IllegalArgumentException.class, () -> calculator.quote(List.of(), FareClass.ECONOMY, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.quote(List.of(outbound), FareClass.ECONOMY, 0, false));
    }
}
