package com.ticketreservation.service;

import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * For each flight in the trip: (fare for the chosen class + checked-baggage fee, if any)
 * × number of passengers. A round trip uses the real fare of the return flight rather than
 * doubling the outbound fare.
 */
public final class FareCalculator {

    public PriceQuote quote(List<Flight> flights, FareClass fareClass, int passengers, boolean checkedBaggage) {
        Objects.requireNonNull(fareClass, "fareClass");
        if (flights == null || flights.isEmpty()) {
            throw new IllegalArgumentException("At least one flight is required");
        }
        if (passengers < 1) {
            throw new IllegalArgumentException("At least one passenger is required");
        }

        List<PriceQuote.Line> lines = new ArrayList<>();
        Money total = Money.zero(flights.get(0).economyPrice().currency());
        for (Flight flight : flights) {
            String route = flight.flightNumber() + " " + flight.origin().code() + "-" + flight.destination().code();

            Money fare = flight.priceFor(fareClass);
            lines.add(line("Fare " + route + ", " + fareClass.displayName(), fare, passengers));
            total = total.plus(fare.times(passengers));

            if (checkedBaggage) {
                Money fee = fareClass.baggageFee();
                lines.add(line("Checked baggage " + route, fee, passengers));
                total = total.plus(fee.times(passengers));
            }
        }
        return new PriceQuote(lines, total);
    }

    private static PriceQuote.Line line(String description, Money unitPrice, int quantity) {
        return new PriceQuote.Line(description, unitPrice, quantity, unitPrice.times(quantity));
    }
}
