package com.ticketreservation.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.User;
import com.ticketreservation.exception.AuthenticationException;
import com.ticketreservation.exception.DataAccessException;
import com.ticketreservation.exception.DuplicateEmailException;
import com.ticketreservation.exception.NotFoundException;
import com.ticketreservation.exception.SeatsUnavailableException;
import com.ticketreservation.exception.ValidationException;
import com.ticketreservation.service.AuthService;
import com.ticketreservation.service.BookingService;
import com.ticketreservation.service.FlightSearchService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON REST API plus the browser front end, served by the JDK's built-in HTTP server.
 *
 * <p>The server only listens on the loopback interface (localhost), so it is not reachable from
 * other machines. Authentication uses bearer tokens from {@link SessionStore}; because the
 * browser never sends them automatically (no cookies), cross-site request forgery is not possible.
 */
public final class WebServer {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Map<String, String> STATIC_FILES = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/app.js", "app.js",
            "/styles.css", "styles.css");

    private final AuthService auth;
    private final FlightSearchService search;
    private final BookingService bookings;
    private final SessionStore sessions;
    private final List<Route> routes = new ArrayList<>();
    private HttpServer server;
    private ExecutorService executor;

    public WebServer(AuthService auth, FlightSearchService search, BookingService bookings, SessionStore sessions) {
        this.auth = Objects.requireNonNull(auth);
        this.search = Objects.requireNonNull(search);
        this.bookings = Objects.requireNonNull(bookings);
        this.sessions = Objects.requireNonNull(sessions);
        registerRoutes();
    }

    /** Starts listening on localhost. Port 0 picks a free port (useful in tests). */
    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        executor = Executors.newFixedThreadPool(16);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            executor.shutdownNow();
        }
    }


    private void registerRoutes() {
        route("POST", "/api/auth/register", r -> {
            Map<String, Object> body = r.jsonBody();
            String password = ApiMapper.requiredString(body, "password");
            User user = auth.register(ApiMapper.requiredString(body, "email"), password.toCharArray(),
                    String.valueOf(body.getOrDefault("passwordConfirmation", password)).toCharArray());
            return Response.json(201, session(user));
        });
        route("POST", "/api/auth/login", r -> {
            Map<String, Object> body = r.jsonBody();
            User user = auth.login(ApiMapper.requiredString(body, "email"),
                    ApiMapper.requiredString(body, "password").toCharArray());
            return Response.json(200, session(user));
        });
        route("POST", "/api/auth/logout", r -> {
            sessions.remove(r.bearerToken());
            return Response.noContent();
        });
        route("GET", "/api/me", r -> Response.json(200, ApiMapper.ordered("email", r.user().email())));

        route("GET", "/api/airports/origins", r -> Response.json(200, ApiMapper.airports(search.originAirports())));
        route("GET", "/api/airports/([A-Z]{3})/destinations",
                r -> Response.json(200, ApiMapper.airports(search.destinationsFrom(r.param(1)))));
        route("GET", "/api/routes/([A-Z]{3})/([A-Z]{3})/dates", r -> Response.json(200,
                search.lowestFaresByDate(r.param(1), r.param(2)).entrySet().stream()
                        .map(e -> ApiMapper.ordered("date", e.getKey().toString(), "lowestFare", ApiMapper.money(e.getValue())))
                        .toList()));
        route("GET", "/api/deals", r -> Response.json(200, search.cheapestDeals(8).stream()
                .map(d -> ApiMapper.flight(d.flight())).toList()));
        route("GET", "/api/flights/search", r -> Response.json(200, ApiMapper.availabilities(
                search.search(r.requiredQuery("from"), r.requiredQuery("to"), r.dateQuery("date")))));
        route("GET", "/api/flights", r -> Response.json(200,
                ApiMapper.availabilities(search.upcomingFlights(r.query("q")))));
        route("GET", "/api/flights/(\\d+)/returns", r -> Response.json(200, ApiMapper.availabilities(
                search.returnFlightsFor(search.getFlight(r.longParam(1)).flight()))));

        route("POST", "/api/quote", r -> {
            ApiMapper.TripSelection trip = ApiMapper.tripSelection(r.jsonBody());
            return Response.json(200, ApiMapper.quote(bookings.quote(trip.outboundFlightId(), trip.returnFlightId(),
                    trip.fareClass(), trip.passengerCount(), trip.checkedBaggage())));
        });
        route("POST", "/api/bookings", r -> {
            Booking booking = bookings.book(r.user(), ApiMapper.bookingRequest(r.jsonBody()));
            return Response.json(201, bookingJson(booking));
        });
        route("GET", "/api/bookings", r -> Response.json(200,
                bookings.bookingsOf(r.user()).stream().map(this::bookingJson).toList()));
        route("GET", "/api/bookings/([A-Za-z0-9]{1,12})",
                r -> Response.json(200, bookingJson(bookings.findBooking(r.user(), r.param(1)))));
        route("POST", "/api/bookings/([A-Za-z0-9]{1,12})/cancel",
                r -> Response.json(200, bookingJson(bookings.cancel(r.user(), r.param(1)))));
    }

    private Map<String, Object> session(User user) {
        return ApiMapper.ordered("token", sessions.create(user), "email", user.email());
    }

    private Map<String, Object> bookingJson(Booking booking) {
        return ApiMapper.booking(booking, bookings.isCancellable(booking));
    }


    private void route(String method, String pathRegex, Handler handler) {
        routes.add(new Route(method, Pattern.compile(pathRegex), handler));
    }

    private void handle(HttpExchange exchange) throws IOException {
        Response response;
        try {
            response = dispatch(exchange);
        } catch (ValidationException | IllegalArgumentException e) {
            response = Response.error(400, e.getMessage());
        } catch (AuthenticationException e) {
            response = Response.error(401, e.getMessage());
        } catch (NotFoundException e) {
            response = Response.error(404, e.getMessage());
        } catch (SeatsUnavailableException | DuplicateEmailException e) {
            response = Response.error(409, e.getMessage());
        } catch (BodyTooLargeException e) {
            response = Response.error(413, "Request body is too large");
        } catch (DataAccessException e) {
            LOG.log(Level.WARNING, "Database error", e);
            response = Response.error(503, "The database is not available right now. Please try again later.");
        } catch (RuntimeException e) {
            // Never leak internal details (stack traces, SQL) to the client.
            LOG.log(Level.SEVERE, "Unexpected error for " + exchange.getRequestURI(), e);
            response = Response.error(500, "Something went wrong. Please try again.");
        }
        send(exchange, response);
    }

    private Response dispatch(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = exchange.getRequestURI().getPath();

        if (!path.startsWith("/api/")) {
            return method.equals("GET") ? staticFile(path) : Response.error(405, "Method not allowed");
        }
        boolean pathMatched = false;
        for (Route route : routes) {
            Matcher matcher = route.pattern().matcher(path);
            if (matcher.matches()) {
                pathMatched = true;
                if (route.method().equals(method)) {
                    return route.handler().handle(new Request(exchange, matcher));
                }
            }
        }
        return pathMatched ? Response.error(405, "Method not allowed") : Response.error(404, "Not found");
    }

    private Response staticFile(String path) throws IOException {
        String file = STATIC_FILES.get(path);
        if (file == null) {
            return Response.error(404, "Not found");
        }
        try (InputStream in = WebServer.class.getResourceAsStream("/web/" + file)) {
            if (in == null) {
                return Response.error(404, "Not found");
            }
            String type = file.endsWith(".html") ? "text/html; charset=utf-8"
                    : file.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : "text/css; charset=utf-8";
            return new Response(200, type, in.readAllBytes());
        }
    }

    private static void send(HttpExchange exchange, Response response) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", response.contentType());
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                        + "frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        byte[] body = response.body();
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        Response handle(Request request) throws IOException;
    }

    private record Route(String method, Pattern pattern, Handler handler) {
    }

    record Response(int status, String contentType, byte[] body) {

        static Response json(int status, Object value) {
            return new Response(status, "application/json; charset=utf-8",
                    Json.write(value).getBytes(StandardCharsets.UTF_8));
        }

        static Response error(int status, String message) {
            return json(status, Map.of("error", message == null ? "Error" : message));
        }

        static Response noContent() {
            return new Response(204, "application/json; charset=utf-8", new byte[0]);
        }
    }

    private static final class BodyTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** One incoming API call, with helpers to read its parameters. */
    private final class Request {
        private final HttpExchange exchange;
        private final Matcher pathMatch;
        private Map<String, String> queryParams;

        Request(HttpExchange exchange, Matcher pathMatch) {
            this.exchange = exchange;
            this.pathMatch = pathMatch;
        }

        String param(int group) {
            return pathMatch.group(group);
        }

        long longParam(int group) {
            try {
                return Long.parseLong(param(group));
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid id: " + param(group));
            }
        }

        String query(String name) {
            if (queryParams == null) {
                queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
            }
            return queryParams.get(name);
        }

        String requiredQuery(String name) {
            String value = query(name);
            if (value == null || value.isBlank()) {
                throw new ValidationException("Query parameter '" + name + "' is required");
            }
            return value;
        }

        LocalDate dateQuery(String name) {
            try {
                return LocalDate.parse(requiredQuery(name));
            } catch (DateTimeParseException e) {
                throw new ValidationException("'" + name + "' must be a date like 2030-01-31");
            }
        }

        String bearerToken() {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return null;
            }
            return header.substring(7).trim();
        }

        User user() {
            return sessions.find(bearerToken())
                    .orElseThrow(() -> new AuthenticationException("Please log in first"));
        }

        Map<String, Object> jsonBody() throws IOException {
            String contentType = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Content-Type")).orElse("");
            if (!contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
                throw new ValidationException("Content-Type must be application/json");
            }
            byte[] bytes;
            try (InputStream in = exchange.getRequestBody()) {
                bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            }
            if (bytes.length > MAX_BODY_BYTES) {
                throw new BodyTooLargeException();
            }
            Object parsed = Json.parse(new String(bytes, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new ValidationException("Request body must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) map;
            return body;
        }

        private static Map<String, String> parseQuery(String rawQuery) {
            Map<String, String> params = new HashMap<>();
            if (rawQuery == null || rawQuery.isEmpty()) {
                return params;
            }
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                params.putIfAbsent(URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
            return params;
        }
    }
}
