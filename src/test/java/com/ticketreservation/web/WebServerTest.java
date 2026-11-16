package com.ticketreservation.web;

import com.ticketreservation.TestData;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.repository.Repositories;
import com.ticketreservation.repository.memory.InMemoryRepositories;
import com.ticketreservation.service.AuthService;
import com.ticketreservation.service.BookingReferenceGenerator;
import com.ticketreservation.service.BookingService;
import com.ticketreservation.service.FareCalculator;
import com.ticketreservation.service.FlightSearchService;
import com.ticketreservation.service.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import static com.ticketreservation.TestData.FRANKFURT;
import static com.ticketreservation.TestData.NOW;
import static com.ticketreservation.TestData.TIRANA;
import static com.ticketreservation.TestData.flight;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real HTTP server on a free port and calls the API like the browser does.
 */
class WebServerTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private WebServer server;
    private Flight outbound;
    private Flight inbound;

    @BeforeEach
    void startServer() throws IOException {
        Repositories repos = InMemoryRepositories.create();
        repos.flights().saveAirport(TIRANA);
        repos.flights().saveAirport(FRANKFURT);
        outbound = repos.flights().saveFlight(flight("TR1", TIRANA, FRANKFURT, NOW.plusDays(2)));
        inbound = repos.flights().saveFlight(flight("TR2", FRANKFURT, TIRANA, NOW.plusDays(4)));

        TestData.MutableClock clock = TestData.clock();
        server = new WebServer(
                new AuthService(repos.users(), new PasswordHasher(1_000, new SecureRandom())),
                new FlightSearchService(repos.flights(), clock),
                new BookingService(repos.flights(), repos.bookings(), new FareCalculator(),
                        new BookingReferenceGenerator(), clock),
                new SessionStore(clock));
        server.start(0);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private record Reply(int status, Object json, HttpResponse<String> raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map() {
            return (Map<String, Object>) json;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list() {
            return (List<Map<String, Object>>) json;
        }
    }

    private Reply call(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
            request.method(method, HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : Json.write(body)));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String text = response.body();
        boolean isJson = response.headers().firstValue("Content-Type").orElse("").startsWith("application/json");
        return new Reply(response.statusCode(), isJson && !text.isEmpty() ? Json.parse(text) : text, response);
    }

    private String register(String email) throws Exception {
        Reply reply = call("POST", "/api/auth/register", Map.of("email", email, "password", "secret123"), null);
        assertEquals(201, reply.status());
        return (String) reply.map().get("token");
    }

    private Map<String, Object> roundTripRequest(int passengers) {
        List<Map<String, String>> list = TestData.passengers(passengers).stream()
                .map(p -> Map.of("firstName", p.firstName(), "lastName", p.lastName(), "phoneNumber", p.phoneNumber()))
                .toList();
        return Map.of("outboundFlightId", outbound.id(), "returnFlightId", inbound.id(),
                "fareClass", "ECONOMY", "checkedBaggage", true, "passengers", list);
    }

    @Test
    void searchEndpointsReturnFlights() throws Exception {
        Reply origins = call("GET", "/api/airports/origins", null, null);
        assertEquals(200, origins.status());
        assertEquals(List.of("FRA", "TIA"), origins.list().stream().map(a -> a.get("code")).toList());

        Reply dates = call("GET", "/api/routes/TIA/FRA/dates", null, null);
        assertEquals(NOW.plusDays(2).toLocalDate().toString(), dates.list().get(0).get("date"));
        assertEquals("80.00", ((Map<?, ?>) dates.list().get(0).get("lowestFare")).get("amount"));

        Reply deals = call("GET", "/api/deals", null, null);
        assertEquals(List.of("FRA", "TIA"), deals.list().stream()
                .map(d -> ((Map<?, ?>) d.get("destination")).get("code")).sorted().toList());

        Reply flights = call("GET", "/api/flights/search?from=TIA&to=FRA&date=" + NOW.plusDays(2).toLocalDate(), null, null);
        Map<String, Object> flight = flights.list().get(0);
        assertEquals("TR1", flight.get("flightNumber"));
        assertEquals(Map.of("amount", "80.00", "currency", "EUR", "formatted", "EUR 80.00"), flight.get("economyPrice"));

        Reply returns = call("GET", "/api/flights/" + outbound.id() + "/returns", null, null);
        assertEquals("TR2", returns.list().get(0).get("flightNumber"));
        assertEquals(2, call("GET", "/api/flights?q=frankfurt", null, null).list().size());
    }

    @Test
    void quoteWorksWithJustAPassengerCount() throws Exception {
        Reply quote = call("POST", "/api/quote", Map.of("outboundFlightId", outbound.id(),
                "returnFlightId", inbound.id(), "fareClass", "economy", "checkedBaggage", true, "passengerCount", 2), null);

        assertEquals(200, quote.status());
        // 2 x (80 + 15) x 2 flights
        assertEquals("380.00", ((Map<?, ?>) quote.map().get("total")).get("amount"));
        assertEquals(4, ((List<?>) quote.map().get("lines")).size());
    }

    @Test
    void registerBookListAndCancel() throws Exception {
        String token = register("alice@example.com");

        Reply booked = call("POST", "/api/bookings", roundTripRequest(2), token);
        assertEquals(201, booked.status());
        String reference = (String) booked.map().get("reference");
        assertEquals("CONFIRMED", booked.map().get("status"));
        assertEquals(true, booked.map().get("cancellable"));
        assertEquals("380.00", ((Map<?, ?>) booked.map().get("totalPrice")).get("amount"));

        Reply list = call("GET", "/api/bookings", null, token);
        assertEquals(List.of(reference), list.list().stream().map(b -> b.get("reference")).toList());

        Reply cancelled = call("POST", "/api/bookings/" + reference + "/cancel", null, token);
        assertEquals(200, cancelled.status());
        assertEquals("CANCELLED", cancelled.map().get("status"));
        assertEquals(false, cancelled.map().get("cancellable"));
        assertEquals(400, call("POST", "/api/bookings/" + reference + "/cancel", null, token).status());
    }

    @Test
    void loginLogoutAndProtectedEndpoints() throws Exception {
        register("bob@example.com");
        assertEquals(401, call("GET", "/api/bookings", null, null).status());
        assertEquals(401, call("GET", "/api/bookings", null, "made-up-token").status());

        Reply wrong = call("POST", "/api/auth/login", Map.of("email", "bob@example.com", "password", "nope12345"), null);
        assertEquals(401, wrong.status());
        assertEquals("Invalid email or password", wrong.map().get("error"));

        Reply login = call("POST", "/api/auth/login", Map.of("email", "BOB@example.com", "password", "secret123"), null);
        assertEquals(200, login.status());
        String token = (String) login.map().get("token");
        assertEquals("bob@example.com", call("GET", "/api/me", null, token).map().get("email"));

        assertEquals(204, call("POST", "/api/auth/logout", null, token).status());
        assertEquals(401, call("GET", "/api/me", null, token).status());
    }

    @Test
    void usersCannotSeeEachOthersBookings() throws Exception {
        String alice = register("alice@example.com");
        String bob = register("bob@example.com");
        String reference = (String) call("POST", "/api/bookings", roundTripRequest(1), alice).map().get("reference");

        assertEquals(404, call("GET", "/api/bookings/" + reference, null, bob).status());
        assertEquals(404, call("POST", "/api/bookings/" + reference + "/cancel", null, bob).status());
        assertEquals(200, call("GET", "/api/bookings/" + reference, null, alice).status());
    }

    @Test
    void badRequestsGetClearErrorsAndStatusCodes() throws Exception {
        String token = register("carol@example.com");

        assertEquals(409, call("POST", "/api/auth/register",
                Map.of("email", "carol@example.com", "password", "secret123"), null).status());
        assertEquals(400, call("POST", "/api/auth/register", Map.of("email", "nope", "password", "secret123"), null).status());
        assertEquals(400, call("POST", "/api/bookings", "{not json", token).status());
        assertEquals(400, call("POST", "/api/bookings", Map.of("outboundFlightId", outbound.id()), token).status());

        Map<String, Object> badName = Map.of("outboundFlightId", outbound.id(), "fareClass", "ECONOMY",
                "passengers", List.of(Map.of("firstName", "J0hn", "lastName", "Doe", "phoneNumber", "0691234567")));
        Reply reply = call("POST", "/api/bookings", badName, token);
        assertEquals(400, reply.status());
        assertTrue(((String) reply.map().get("error")).startsWith("Passenger 1:"));

        assertEquals(404, call("GET", "/api/flights/999/returns", null, null).status());
        assertEquals(400, call("GET", "/api/flights/search?from=TIA&to=FRA&date=tomorrow", null, null).status());
        assertEquals(404, call("GET", "/api/nothing-here", null, null).status());
        assertEquals(405, call("DELETE", "/api/bookings", null, token).status());
        assertEquals(413, call("POST", "/api/quote", "{\"x\":\"" + "a".repeat(70_000) + "\"}", null).status());

        HttpResponse<String> wrongType = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + server.port() + "/api/auth/login"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, wrongType.statusCode());
    }

    @Test
    void servesTheFrontEndWithSecurityHeaders() throws Exception {
        Reply page = call("GET", "/", null, null);
        assertEquals(200, page.status());
        assertTrue(((String) page.json()).contains("<title>SkyReserve"));
        assertTrue(page.raw().headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'"));
        assertEquals("nosniff", page.raw().headers().firstValue("X-Content-Type-Options").orElse(""));

        assertEquals(200, call("GET", "/app.js", null, null).status());
        assertEquals(200, call("GET", "/styles.css", null, null).status());
        assertEquals(404, call("GET", "/../pom.xml", null, null).status());
        Reply missing = call("GET", "/secret.txt", null, null);
        assertEquals(404, missing.status());
        assertFalse(missing.raw().body().contains("Exception"));
    }
}
