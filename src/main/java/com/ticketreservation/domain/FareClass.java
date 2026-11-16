package com.ticketreservation.domain;

/**
 * Cabin class of a ticket. Each class has its own checked-baggage fee per passenger per flight.
 */
public enum FareClass {
    ECONOMY("Economy", Money.euros(15)),
    FIRST("First", Money.euros(25));

    private final String displayName;
    private final Money baggageFee;

    FareClass(String displayName, Money baggageFee) {
        this.displayName = displayName;
        this.baggageFee = baggageFee;
    }

    public String displayName() {
        return displayName;
    }

    public Money baggageFee() {
        return baggageFee;
    }
}
