package com.ticketreservation.repository.jdbc;

import com.ticketreservation.domain.Aircraft;
import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.Money;
import com.ticketreservation.domain.NewFlight;
import com.ticketreservation.repository.FlightRepository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

public final class JdbcFlightRepository implements FlightRepository {

    private static final String FLIGHT_COLUMNS = """
            SELECT f.id, f.flight_number, f.departure_time, f.arrival_time, f.aircraft, f.currency,
                   f.economy_price, f.first_class_price, f.available_seats,
                   o.code AS o_code, o.name AS o_name, o.city AS o_city, o.country AS o_country,
                   d.code AS d_code, d.name AS d_name, d.city AS d_city, d.country AS d_country
            FROM flights f
            JOIN airports o ON o.code = f.origin_code
            JOIN airports d ON d.code = f.destination_code
            """;
    private static final String ORDER_BY_DEPARTURE = " ORDER BY f.departure_time, f.flight_number";

    private final JdbcTemplate jdbc;

    JdbcFlightRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Airport saveAirport(Airport airport) {
        jdbc.inTransaction(c -> {
            int updated = JdbcTemplate.update(c, "UPDATE airports SET name = ?, city = ?, country = ? WHERE code = ?",
                    airport.name(), airport.city(), airport.country(), airport.code());
            if (updated == 0) {
                JdbcTemplate.update(c, "INSERT INTO airports (code, name, city, country) VALUES (?, ?, ?, ?)",
                        airport.code(), airport.name(), airport.city(), airport.country());
            }
            return null;
        });
        return airport;
    }

    @Override
    public List<Airport> findAllAirports() {
        return jdbc.query("SELECT code, name, city, country FROM airports ORDER BY code", JdbcFlightRepository::mapAirport);
    }

    @Override
    public Optional<Airport> findAirport(String code) {
        return jdbc.queryOne("SELECT code, name, city, country FROM airports WHERE code = ?",
                JdbcFlightRepository::mapAirport, code);
    }

    @Override
    public Flight saveFlight(NewFlight f) {
        return jdbc.withConnection(c -> insertFlight(c, f));
    }

    /** Inserts all flights in one transaction on one connection. */
    @Override
    public List<Flight> saveFlights(List<NewFlight> newFlights) {
        return jdbc.inTransaction(c -> {
            List<Flight> saved = new ArrayList<>(newFlights.size());
            for (NewFlight f : newFlights) {
                saved.add(insertFlight(c, f));
            }
            return saved;
        });
    }

    private static Flight insertFlight(Connection c, NewFlight f) throws SQLException {
        long id = JdbcTemplate.insert(c, """
                        INSERT INTO flights (flight_number, origin_code, destination_code, departure_time, arrival_time,
                                             aircraft, currency, economy_price, first_class_price, available_seats)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                f.flightNumber(), f.origin().code(), f.destination().code(),
                Timestamp.valueOf(f.departure()), Timestamp.valueOf(f.arrival()), f.aircraft().name(),
                f.economyPrice().currency().getCurrencyCode(), f.economyPrice().amount(),
                f.firstClassPrice().amount(), f.aircraft().capacity());
        return f.withId(id);
    }

    @Override
    public Optional<FlightAvailability> findById(long flightId) {
        return jdbc.withConnection(c -> findById(c, flightId));
    }

    Optional<FlightAvailability> findById(Connection c, long flightId) throws SQLException {
        return JdbcTemplate.queryOne(c, FLIGHT_COLUMNS + " WHERE f.id = ?", JdbcFlightRepository::mapAvailability, flightId);
    }

    @Override
    public List<FlightAvailability> findDepartingBetween(LocalDateTime from, LocalDateTime to) {
        return jdbc.query(FLIGHT_COLUMNS + " WHERE f.departure_time >= ? AND f.departure_time < ?" + ORDER_BY_DEPARTURE,
                JdbcFlightRepository::mapAvailability, Timestamp.valueOf(from), Timestamp.valueOf(to));
    }

    @Override
    public List<FlightAvailability> findByRoute(String originCode, String destinationCode,
                                                LocalDateTime from, LocalDateTime to) {
        return jdbc.query(FLIGHT_COLUMNS + """
                         WHERE f.origin_code = ? AND f.destination_code = ?
                           AND f.departure_time >= ? AND f.departure_time < ?""" + ORDER_BY_DEPARTURE,
                JdbcFlightRepository::mapAvailability,
                originCode, destinationCode, Timestamp.valueOf(from), Timestamp.valueOf(to));
    }

    @Override
    public List<Airport> findDestinationsFrom(String originCode, LocalDateTime after) {
        return jdbc.query("""
                        SELECT a.code, a.name, a.city, a.country FROM airports a
                        WHERE a.code IN (SELECT f.destination_code FROM flights f
                                         WHERE f.origin_code = ? AND f.departure_time > ?)
                        ORDER BY a.code""",
                JdbcFlightRepository::mapAirport, originCode, Timestamp.valueOf(after));
    }

    @Override
    public List<Airport> findOriginsWithFlightsAfter(LocalDateTime after) {
        return jdbc.query("""
                        SELECT a.code, a.name, a.city, a.country FROM airports a
                        WHERE a.code IN (SELECT f.origin_code FROM flights f WHERE f.departure_time > ?)
                        ORDER BY a.code""",
                JdbcFlightRepository::mapAirport, Timestamp.valueOf(after));
    }

    @Override
    public boolean isEmpty() {
        return jdbc.queryOne("SELECT COUNT(*) FROM flights", rs -> rs.getLong(1)).orElse(0L) == 0L;
    }

    private static Airport mapAirport(ResultSet rs) throws SQLException {
        return new Airport(rs.getString("code").trim(), rs.getString("name"), rs.getString("city"), rs.getString("country"));
    }

    private static FlightAvailability mapAvailability(ResultSet rs) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency").trim());
        Airport origin = new Airport(rs.getString("o_code").trim(), rs.getString("o_name"),
                rs.getString("o_city"), rs.getString("o_country"));
        Airport destination = new Airport(rs.getString("d_code").trim(), rs.getString("d_name"),
                rs.getString("d_city"), rs.getString("d_country"));
        Flight flight = new Flight(
                rs.getLong("id"),
                rs.getString("flight_number"),
                origin,
                destination,
                rs.getTimestamp("departure_time").toLocalDateTime(),
                rs.getTimestamp("arrival_time").toLocalDateTime(),
                Aircraft.valueOf(rs.getString("aircraft")),
                Money.of(rs.getBigDecimal("economy_price"), currency),
                Money.of(rs.getBigDecimal("first_class_price"), currency));
        return new FlightAvailability(flight, rs.getInt("available_seats"));
    }
}
