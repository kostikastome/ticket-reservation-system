package com.ticketreservation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void scalesAmountToCurrencyMinorUnits() {
        assertEquals(new BigDecimal("10.50"), Money.euros("10.5").amount());
        assertEquals("EUR 10.50", Money.euros("10.5").toString());
    }

    @Test
    void addsWithoutFloatingPointErrors() {
        Money total = Money.euros("0.10").plus(Money.euros("0.20"));
        assertEquals(Money.euros("0.30"), total);
    }

    @Test
    void multipliesByQuantity() {
        assertEquals(Money.euros("239.97"), Money.euros("79.99").times(3));
        assertTrue(Money.euros("5").times(0).isZero());
    }

    @Test
    void equalAmountsWithDifferentScaleAreEqual() {
        assertEquals(Money.euros("15"), Money.euros("15.00"));
        assertEquals(Money.euros("15").hashCode(), Money.euros("15.00").hashCode());
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> Money.euros("-1"));
        assertThrows(IllegalArgumentException.class, () -> Money.euros("1").times(-1));
    }

    @Test
    void rejectsMixingCurrencies() {
        Money usd = Money.of("10", Currency.getInstance("USD"));
        assertThrows(IllegalArgumentException.class, () -> Money.euros("10").plus(usd));
        assertThrows(IllegalArgumentException.class, () -> Money.euros("10").compareTo(usd));
    }

    @Test
    void comparesAmounts() {
        assertTrue(Money.euros("10").compareTo(Money.euros("9.99")) > 0);
    }
}
