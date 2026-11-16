package com.ticketreservation.repository.jdbc;

import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.BookingStatus;
import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.Money;
import com.ticketreservation.domain.NewBooking;
import com.ticketreservation.domain.Passenger;
import com.ticketreservation.exception.SeatsUnavailableException;
import com.ticketreservation.repository.BookingRepository;
import com.ticketreservation.repository.jdbc.JdbcTemplate.SqlNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

public final class JdbcBookingRepository implements BookingRepository {

    private static final String BOOKING_COLUMNS = """
            SELECT id, booking_reference, user_id, outbound_flight_id, return_flight_id, fare_class,
                   checked_baggage, currency, total_price, status, created_at
            FROM bookings
            """;

    private final JdbcTemplate jdbc;
    private final JdbcFlightRepository flights;

    JdbcBookingRepository(JdbcTemplate jdbc, JdbcFlightRepository flights) {
        this.jdbc = jdbc;
        this.flights = flights;
    }

    @Override
    public Booking create(NewBooking booking) {
        return jdbc.inTransaction(c -> {
            // Update flights in id order so two concurrent round-trip bookings cannot deadlock.
            List<Flight> flightsInLockOrder = new ArrayList<>(booking.toBooking(0).flights());
            flightsInLockOrder.sort(Comparator.comparingLong(Flight::id));
            for (Flight flight : flightsInLockOrder) {
                reserveSeats(c, flight, booking.seatCount());
            }

            long id = JdbcTemplate.insert(c, """
                            INSERT INTO bookings (booking_reference, user_id, outbound_flight_id, return_flight_id,
                                                  fare_class, checked_baggage, currency, total_price, status, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    booking.reference(),
                    booking.userId(),
                    booking.outboundFlight().id(),
                    booking.returnFlightOptional().<Object>map(Flight::id).orElse(new SqlNull(Types.BIGINT)),
                    booking.fareClass().name(),
                    booking.checkedBaggage(),
                    booking.totalPrice().currency().getCurrencyCode(),
                    booking.totalPrice().amount(),
                    BookingStatus.CONFIRMED.name(),
                    toTimestamp(booking.createdAt()));

            List<Passenger> passengers = booking.passengers();
            for (int i = 0; i < passengers.size(); i++) {
                Passenger p = passengers.get(i);
                JdbcTemplate.update(c, """
                                INSERT INTO booking_passengers (booking_id, seq_no, first_name, last_name, phone_number)
                                VALUES (?, ?, ?, ?, ?)""",
                        id, i + 1, p.firstName(), p.lastName(), p.phoneNumber());
            }
            return booking.toBooking(id);
        });
    }

    /**
     * The WHERE clause makes the check and the decrement one atomic statement, so two customers
     * racing for the last seat cannot both get it, whatever the isolation level.
     */
    private static void reserveSeats(Connection c, Flight flight, int seats) throws SQLException {
        int updated = JdbcTemplate.update(c,
                "UPDATE flights SET available_seats = available_seats - ? WHERE id = ? AND available_seats >= ?",
                seats, flight.id(), seats);
        if (updated == 0) {
            throw new SeatsUnavailableException("Flight " + flight.flightNumber() + " does not have "
                    + seats + " free seat(s) any more");
        }
    }

    @Override
    public Optional<Booking> cancel(long bookingId) {
        return jdbc.inTransaction(c -> {
            // Conditional update: only one of several concurrent cancellations can succeed,
            // so seats are never given back twice.
            int updated = JdbcTemplate.update(c,
                    "UPDATE bookings SET status = ? WHERE id = ? AND status = ?",
                    BookingStatus.CANCELLED.name(), bookingId, BookingStatus.CONFIRMED.name());
            if (updated == 0) {
                return Optional.<Booking>empty();
            }
            Booking cancelled = JdbcTemplate.queryOne(c, BOOKING_COLUMNS + " WHERE id = ?", BookingRow::map, bookingId)
                    .orElseThrow()
                    .toBooking(c, flights);
            for (Flight flight : cancelled.flights()) {
                JdbcTemplate.update(c, "UPDATE flights SET available_seats = available_seats + ? WHERE id = ?",
                        cancelled.seatCount(), flight.id());
            }
            return Optional.of(cancelled);
        });
    }

    @Override
    public Optional<Booking> findByReference(String reference) {
        return jdbc.withConnection(c -> {
            Optional<BookingRow> row = JdbcTemplate.queryOne(c, BOOKING_COLUMNS + " WHERE booking_reference = ?",
                    BookingRow::map, reference);
            return row.isPresent() ? Optional.of(row.get().toBooking(c, flights)) : Optional.<Booking>empty();
        });
    }

    @Override
    public List<Booking> findByUserId(long userId) {
        return jdbc.withConnection(c -> {
            List<BookingRow> rows = JdbcTemplate.query(c,
                    BOOKING_COLUMNS + " WHERE user_id = ? ORDER BY created_at DESC, id DESC", BookingRow::map, userId);
            List<Booking> result = new ArrayList<>(rows.size());
            for (BookingRow row : rows) {
                result.add(row.toBooking(c, flights));
            }
            return result;
        });
    }

    private static Timestamp toTimestamp(Instant instant) {
        // Stored as UTC wall-clock time so the value does not depend on the server's time zone.
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    /** Raw bookings row, before its flights and passengers are loaded. */
    private record BookingRow(long id, String reference, long userId, long outboundFlightId, Long returnFlightId,
                              FareClass fareClass, boolean checkedBaggage, Money totalPrice,
                              BookingStatus status, Instant createdAt) {

        static BookingRow map(ResultSet rs) throws SQLException {
            long returnId = rs.getLong("return_flight_id");
            Long returnFlightId = rs.wasNull() ? null : returnId;
            Currency currency = Currency.getInstance(rs.getString("currency").trim());
            return new BookingRow(
                    rs.getLong("id"),
                    rs.getString("booking_reference").trim(),
                    rs.getLong("user_id"),
                    rs.getLong("outbound_flight_id"),
                    returnFlightId,
                    FareClass.valueOf(rs.getString("fare_class")),
                    rs.getBoolean("checked_baggage"),
                    Money.of(rs.getBigDecimal("total_price"), currency),
                    BookingStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toLocalDateTime().toInstant(ZoneOffset.UTC));
        }

        Booking toBooking(Connection c, JdbcFlightRepository flights) throws SQLException {
            Flight outbound = loadFlight(c, flights, outboundFlightId);
            Flight inbound = returnFlightId == null ? null : loadFlight(c, flights, returnFlightId);
            List<Passenger> passengers = JdbcTemplate.query(c, """
                            SELECT first_name, last_name, phone_number FROM booking_passengers
                            WHERE booking_id = ? ORDER BY seq_no""",
                    rs -> new Passenger(rs.getString("first_name"), rs.getString("last_name"),
                            rs.getString("phone_number")),
                    id);
            return new Booking(id, reference, userId, outbound, inbound, fareClass, checkedBaggage, passengers,
                    totalPrice, status, createdAt);
        }

        private static Flight loadFlight(Connection c, JdbcFlightRepository flights, long flightId)
                throws SQLException {
            return flights.findById(c, flightId)
                    .map(FlightAvailability::flight)
                    .orElseThrow(() -> new SQLException("Booking refers to missing flight " + flightId));
        }
    }
}
