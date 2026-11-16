package com.ticketreservation.service;

import com.ticketreservation.domain.Money;

import java.util.List;
import java.util.Objects;

/**
 * An itemised price: one line per fare or fee, and the total.
 */
public record PriceQuote(List<Line> lines, Money total) {

    public PriceQuote {
        lines = List.copyOf(lines);
        Objects.requireNonNull(total, "total");
    }

    /** e.g. "Fare TR110 TIA-FRA, Economy", unit price EUR 99.00, quantity 2, amount EUR 198.00. */
    public record Line(String description, Money unitPrice, int quantity, Money amount) {
    }
}
