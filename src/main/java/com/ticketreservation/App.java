package com.ticketreservation;

import com.ticketreservation.cli.ConsoleApp;
import com.ticketreservation.cli.Terminal;
import com.ticketreservation.config.AppConfig;
import com.ticketreservation.exception.DataAccessException;
import com.ticketreservation.repository.Repositories;
import com.ticketreservation.repository.jdbc.ConnectionProvider;
import com.ticketreservation.repository.jdbc.JdbcRepositories;
import com.ticketreservation.repository.memory.InMemoryRepositories;
import com.ticketreservation.service.AuthService;
import com.ticketreservation.service.BookingReferenceGenerator;
import com.ticketreservation.service.BookingService;
import com.ticketreservation.service.DemoDataSeeder;
import com.ticketreservation.service.FareCalculator;
import com.ticketreservation.service.FlightSearchService;
import com.ticketreservation.service.PasswordHasher;
import com.ticketreservation.web.SessionStore;
import com.ticketreservation.web.WebServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.time.Clock;
import java.util.List;

/**
 * Entry point: reads the configuration, wires the components together and starts the web UI
 * (default) or the console UI ({@code --console}).
 * Wiring is done by hand (constructor injection) to keep the project free of frameworks.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.fromEnvironment();
            Clock clock = Clock.systemDefaultZone();
            Repositories repositories = createRepositories(config);

            if (config.seedDemoData()) {
                int created = new DemoDataSeeder(repositories.flights(), clock).seedIfEmpty();
                if (created > 0) {
                    System.out.println("Loaded " + created + " demo flights.");
                }
            }

            AuthService auth = new AuthService(repositories.users(), new PasswordHasher());
            FlightSearchService search = new FlightSearchService(repositories.flights(), clock);
            BookingService bookings = new BookingService(repositories.flights(), repositories.bookings(),
                    new FareCalculator(), new BookingReferenceGenerator(), clock);

            if (List.of(args).contains("--console")) {
                new ConsoleApp(Terminal.system(), auth, search, bookings).run();
            } else {
                startWeb(new WebServer(auth, search, bookings, new SessionStore(clock)), config.port(),
                        !List.of(args).contains("--no-browser"));
            }
        } catch (IllegalArgumentException | DataAccessException e) {
            System.err.println("Could not start: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void startWeb(WebServer server, int port, boolean openBrowser) {
        try {
            server.start(port);
        } catch (BindException e) {
            System.err.println("Could not start: port " + port + " is already in use. "
                    + "Close the other program or set TR_PORT to another port.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Could not start the web server: " + e.getMessage());
            System.exit(1);
        }
        String url = "http://localhost:" + server.port();
        System.out.println();
        System.out.println("Ticket Reservation System is running at " + url);
        System.out.println("Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        if (openBrowser) {
            openInBrowser(url);
        }
    }

    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // Opening the browser is a convenience; the URL is printed above.
        }
    }

    static Repositories createRepositories(AppConfig config) {
        return switch (config.storage()) {
            case MEMORY -> {
                System.out.println("Storage: in-memory (data is lost on exit; set TR_STORAGE=jdbc to use MySQL)");
                yield InMemoryRepositories.create();
            }
            case JDBC -> {
                System.out.println("Storage: " + config.dbUrl());
                ConnectionProvider connections =
                        ConnectionProvider.driverManager(config.dbUrl(), config.dbUser(), config.dbPassword());
                if (JdbcRepositories.initializeSchemaIfMissing(connections, JdbcRepositories.DEFAULT_SCHEMA)) {
                    System.out.println("Created database schema.");
                }
                yield JdbcRepositories.create(connections);
            }
        };
    }
}
