package com.ticketreservation.web;

import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.Money;
import com.ticketreservation.domain.Passenger;
import com.ticketreservation.exception.ValidationException;
import com.ticketreservation.service.BookingRequest;
import com.ticketreservation.service.PriceQuote;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Converts between domain objects and the JSON shapes of the web API. Keeping this separate
 * means the domain classes know nothing about JSON.
 */
final class ApiMapper {

    private ApiMapper() {
    }

    static Map<String, Object> airport(Airport a) {
        return ordered("code", a.code(), "name", a.name(), "city", a.city(), "country", a.country());
    }

    static List<Map<String, Object>> airports(List<Airport> airports) {
        return airports.stream().map(ApiMapper::airport).toList();
    }

    static Map<String, Object> flight(Flight f) {
        return ordered(
                "id", f.id(),
                "flightNumber", f.flightNumber(),
                "origin", airport(f.origin()),
                "destination", airport(f.destination()),
                "departure", f.departure().toString(),
                "arrival", f.arrival().toString(),
                "durationMinutes", f.duration().toMinutes(),
                "aircraft", f.aircraft().displayName(),
                "economyPrice", money(f.economyPrice()),
                "firstClassPrice", money(f.firstClassPrice()));
    }

    static Map<String, Object> availability(FlightAvailability fa) {
        Map<String, Object> map = flight(fa.flight());
        map.put("availableSeats", fa.availableSeats());
        return map;
    }

    static List<Map<String, Object>> availabilities(List<FlightAvailability> flights) {
        return flights.stream().map(ApiMapper::availability).toList();
    }

    /** Amounts are strings so JavaScript never turns 0.1 + 0.2 into 0.30000000000000004. */
    static Map<String, Object> money(Money m) {
        return ordered("amount", m.amount().toPlainString(), "currency", m.currency().getCurrencyCode(),
                "formatted", m.toString());
    }

    static Map<String, Object> quote(PriceQuote q) {
        List<Map<String, Object>> lines = q.lines().stream()
                .map(l -> ordered("description", l.description(), "unitPrice", money(l.unitPrice()),
                        "quantity", l.quantity(), "amount", money(l.amount())))
                .toList();
        return ordered("lines", lines, "total", money(q.total()));
    }

    static Map<String, Object> booking(Booking b, boolean cancellable) {
        return ordered(
                "reference", b.reference(),
                "status", b.status().name(),
                "fareClass", b.fareClass().name(),
                "checkedBaggage", b.checkedBaggage(),
                "outbound", flight(b.outboundFlight()),
                "return", b.returnFlightOptional().map(ApiMapper::flight).orElse(null),
                "passengers", b.passengers().stream()
                        .map(p -> ordered("firstName", p.firstName(), "lastName", p.lastName(),
                                "phoneNumber", p.phoneNumber()))
                        .toList(),
                "totalPrice", money(b.totalPrice()),
                "createdAt", b.createdAt().toString(),
                "cancellable", cancellable);
    }

    /** The trip part of a quote or booking request: flights, class, passenger count and baggage. */
    record TripSelection(long outboundFlightId, OptionalLong returnFlightId, FareClass fareClass,
                         int passengerCount, boolean checkedBaggage) {
    }

    /**
     * Reads a quote request. It may contain full passenger details or just
     * {@code "passengerCount"}, so the UI can show a price before names are typed in.
     */
    static TripSelection tripSelection(Map<String, Object> body) {
        int count = body.get("passengers") instanceof List<?> list ? list.size()
                : Math.toIntExact(Math.min(requiredLong(body, "passengerCount"), Integer.MAX_VALUE));
        return new TripSelection(requiredLong(body, "outboundFlightId"), optionalLong(body, "returnFlightId"),
                fareClass(body), count, checkedBaggage(body));
    }

    /** Reads the body of a booking request, including every passenger's details. */
    static BookingRequest bookingRequest(Map<String, Object> body) {
        long outboundId = requiredLong(body, "outboundFlightId");
        OptionalLong returnFlightId = optionalLong(body, "returnFlightId");
        FareClass fareClass = fareClass(body);
        boolean baggage = checkedBaggage(body);

        if (!(body.get("passengers") instanceof List<?> rawPassengers)) {
            throw new ValidationException("passengers must be a list");
        }
        List<Passenger> passengers = new ArrayList<>();
        for (int i = 0; i < rawPassengers.size(); i++) {
            if (!(rawPassengers.get(i) instanceof Map<?, ?> p)) {
                throw new ValidationException("Passenger " + (i + 1) + " must be an object");
            }
            try {
                passengers.add(new Passenger(asString(p.get("firstName")), asString(p.get("lastName")),
                        asString(p.get("phoneNumber"))));
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Passenger " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new BookingRequest(outboundId, returnFlightId, fareClass, baggage, passengers);
    }

    private static FareClass fareClass(Map<String, Object> body) {
        try {
            return FareClass.valueOf(requiredString(body, "fareClass").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("fareClass must be ECONOMY or FIRST");
        }
    }

    private static boolean checkedBaggage(Map<String, Object> body) {
        Object baggage = body.get("checkedBaggage");
        if (baggage != null && !(baggage instanceof Boolean)) {
            throw new ValidationException("checkedBaggage must be true or false");
        }
        return Boolean.TRUE.equals(baggage);
    }

    private static OptionalLong optionalLong(Map<String, Object> body, String field) {
        Object value = body.get(field);
        return value == null ? OptionalLong.empty() : OptionalLong.of(toLong(value, field));
    }

    static String requiredString(Map<String, Object> body, String field) {
        if (!(body.get(field) instanceof String s)) {
            throw new ValidationException(field + " is required");
        }
        return s;
    }

    private static long requiredLong(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            throw new ValidationException(field + " is required");
        }
        return toLong(value, field);
    }

    private static long toLong(Object value, String field) {
        if (value instanceof BigDecimal d) {
            try {
                return d.longValueExact();
            } catch (ArithmeticException e) {
                throw new ValidationException(field + " must be a whole number");
            }
        }
        throw new ValidationException(field + " must be a number");
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /** Builds a mutable, insertion-ordered map from alternating keys and values. */
    static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
