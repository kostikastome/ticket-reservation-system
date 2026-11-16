package com.ticketreservation.cli;

import com.ticketreservation.domain.Airport;
import com.ticketreservation.domain.Booking;
import com.ticketreservation.domain.BookingStatus;
import com.ticketreservation.domain.FareClass;
import com.ticketreservation.domain.Flight;
import com.ticketreservation.domain.FlightAvailability;
import com.ticketreservation.domain.Passenger;
import com.ticketreservation.domain.User;
import com.ticketreservation.exception.DataAccessException;
import com.ticketreservation.exception.ReservationException;
import com.ticketreservation.service.AuthService;
import com.ticketreservation.service.BookingRequest;
import com.ticketreservation.service.BookingService;
import com.ticketreservation.service.FlightSearchService;
import com.ticketreservation.service.PriceQuote;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Interactive text interface. It only talks to the services; all business rules live there.
 */
public final class ConsoleApp {

    static final String APP_NAME = "TICKET RESERVATION SYSTEM";
    private static final int MAX_ROWS = 25;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final Terminal terminal;
    private final AuthService auth;
    private final FlightSearchService search;
    private final BookingService bookingService;

    private User currentUser;

    public ConsoleApp(Terminal terminal, AuthService auth, FlightSearchService search, BookingService bookingService) {
        this.terminal = Objects.requireNonNull(terminal);
        this.auth = Objects.requireNonNull(auth);
        this.search = Objects.requireNonNull(search);
        this.bookingService = Objects.requireNonNull(bookingService);
    }

    /** Runs until the user chooses Exit or input ends. */
    public void run() {
        banner();
        try {
            boolean running = true;
            while (running) {
                running = currentUser == null ? guestMenu() : customerMenu();
            }
        } catch (Terminal.EndOfInputException e) {
            terminal.println();
        }
        terminal.println("Goodbye!");
    }


    private boolean guestMenu() {
        terminal.println();
        terminal.println("Main menu");
        terminal.println("  1) Log in");
        terminal.println("  2) Create an account");
        terminal.println("  3) Browse upcoming flights");
        terminal.println("  0) Exit");
        switch (readInt("Choose an option: ", 0, 3)) {
            case 1 -> safely(this::login);
            case 2 -> safely(this::register);
            case 3 -> safely(this::browseFlights);
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean customerMenu() {
        terminal.println();
        terminal.println("Logged in as " + currentUser.email());
        terminal.println("  1) Book a flight");
        terminal.println("  2) My bookings");
        terminal.println("  3) Cancel a booking");
        terminal.println("  4) Browse upcoming flights");
        terminal.println("  5) Log out");
        terminal.println("  0) Exit");
        switch (readInt("Choose an option: ", 0, 5)) {
            case 1 -> safely(this::bookFlight);
            case 2 -> safely(this::showMyBookings);
            case 3 -> safely(this::cancelBooking);
            case 4 -> safely(this::browseFlights);
            case 5 -> {
                terminal.println("You have been logged out.");
                currentUser = null;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Shows business errors to the user and returns to the menu instead of crashing. */
    private void safely(Runnable action) {
        try {
            action.run();
        } catch (ReservationException e) {
            terminal.println("Error: " + e.getMessage());
        } catch (DataAccessException e) {
            terminal.println("Error: the database is not available right now. Please try again later.");
        }
    }


    private void login() {
        heading("Log in");
        String email = terminal.readLine("Email: ");
        char[] password = terminal.readPassword("Password: ");
        currentUser = auth.login(email, password);
        terminal.println("Welcome back, " + currentUser.email() + "!");
    }

    private void register() {
        heading("Create an account");
        terminal.println("Passwords need at least " + AuthService.MIN_PASSWORD_LENGTH
                + " characters, including letters and digits.");
        String email = terminal.readLine("Email: ");
        char[] password = terminal.readPassword("Password: ");
        char[] confirmation = terminal.readPassword("Repeat password: ");
        currentUser = auth.register(email, password, confirmation);
        terminal.println("Account created. You are now logged in as " + currentUser.email() + ".");
    }


    private void browseFlights() {
        heading("Upcoming flights");
        String filter = terminal.readLine("Filter by city, country, airport code or flight number (Enter for all): ");
        List<FlightAvailability> flights = search.upcomingFlights(filter);
        if (flights.isEmpty()) {
            terminal.println("No upcoming flights match \"" + filter.trim() + "\".");
            return;
        }
        printFlights(flights.subList(0, Math.min(flights.size(), MAX_ROWS)), false);
        if (flights.size() > MAX_ROWS) {
            terminal.println("... and " + (flights.size() - MAX_ROWS) + " more. Use a filter to narrow the list.");
        }
    }


    private void bookFlight() {
        heading("Book a flight");
        Optional<Airport> origin = choose("Where are you flying from?", search.originAirports(), Airport::label);
        if (origin.isEmpty()) {
            return;
        }
        Optional<Airport> destination = choose("Where are you flying to?",
                search.destinationsFrom(origin.get().code()), Airport::label);
        if (destination.isEmpty()) {
            return;
        }
        List<LocalDate> dates = search.availableDates(origin.get().code(), destination.get().code());
        Optional<LocalDate> date = choose("Which day do you want to fly?", dates, DATE::format);
        if (date.isEmpty()) {
            return;
        }

        List<FlightAvailability> outboundOptions =
                search.search(origin.get().code(), destination.get().code(), date.get());
        Optional<FlightAvailability> outbound = chooseFlight("Available flights", outboundOptions);
        if (outbound.isEmpty()) {
            return;
        }

        Optional<FlightAvailability> inbound = Optional.empty();
        terminal.println();
        terminal.println("Trip type");
        terminal.println("  1) One way");
        terminal.println("  2) Round trip");
        if (readInt("Choose: ", 1, 2) == 2) {
            List<FlightAvailability> returnOptions = search.returnFlightsFor(outbound.get().flight());
            List<LocalDate> returnDates = returnOptions.stream()
                    .filter(f -> f.availableSeats() > 0)
                    .map(f -> f.flight().departure().toLocalDate())
                    .distinct()
                    .toList();
            Optional<LocalDate> returnDate = choose("Which day do you want to fly back?", returnDates, DATE::format);
            if (returnDate.isEmpty()) {
                return;
            }
            inbound = chooseFlight("Return flights", returnOptions.stream()
                    .filter(f -> f.flight().departure().toLocalDate().equals(returnDate.get()))
                    .toList());
            if (inbound.isEmpty()) {
                return;
            }
        }

        terminal.println();
        terminal.println("Travel class");
        FareClass[] classes = FareClass.values();
        for (int i = 0; i < classes.length; i++) {
            terminal.println("  " + (i + 1) + ") " + classes[i].displayName()
                    + " (checked bag " + classes[i].baggageFee() + " per flight)");
        }
        FareClass fareClass = classes[readInt("Choose: ", 1, classes.length) - 1];

        int seatsLeft = Math.min(outbound.get().availableSeats(),
                inbound.map(FlightAvailability::availableSeats).orElse(Integer.MAX_VALUE));
        int maxPassengers = Math.min(BookingService.MAX_PASSENGERS_PER_BOOKING, seatsLeft);
        int passengerCount = readInt("Number of passengers (1-" + maxPassengers + "): ", 1, maxPassengers);
        List<Passenger> passengers = new ArrayList<>();
        for (int i = 1; i <= passengerCount; i++) {
            passengers.add(readPassenger(i));
        }
        boolean baggage = readYesNo("Add one checked bag per passenger? (y/n): ");

        OptionalLong returnId = inbound.map(f -> OptionalLong.of(f.flight().id())).orElse(OptionalLong.empty());
        BookingRequest request = new BookingRequest(outbound.get().flight().id(), returnId, fareClass, baggage, passengers);
        PriceQuote quote = bookingService.quote(request);
        printQuote(quote);

        if (!readYesNo("Confirm and pay " + quote.total() + "? (y/n): ")) {
            terminal.println("Booking cancelled. Nothing was charged.");
            return;
        }
        Booking booking = bookingService.book(currentUser, request);
        terminal.println();
        terminal.println("Booking confirmed! Your reference is " + booking.reference() + ".");
        printBookingDetails(booking);
    }

    private Passenger readPassenger(int number) {
        terminal.println();
        terminal.println("Passenger " + number);
        String first = readValid("  First name: ", Passenger::isValidName,
                "Please use letters only (spaces, hyphens and apostrophes are allowed).");
        String last = readValid("  Last name: ", Passenger::isValidName,
                "Please use letters only (spaces, hyphens and apostrophes are allowed).");
        String phone = readValid("  Phone number: ", Passenger::isValidPhone,
                "Please enter 8-15 digits, optionally starting with +.");
        return new Passenger(first, last, phone);
    }

    private void printQuote(PriceQuote quote) {
        heading("Price summary");
        TextTable table = new TextTable("Item", "Unit price", "Qty", "Amount").alignRight(1, 2, 3);
        for (PriceQuote.Line line : quote.lines()) {
            table.addRow(line.description(), line.unitPrice(), line.quantity(), line.amount());
        }
        table.render().forEach(terminal::println);
        terminal.println("Total: " + quote.total());
    }


    private void showMyBookings() {
        heading("My bookings");
        List<Booking> bookings = bookingService.bookingsOf(currentUser);
        if (bookings.isEmpty()) {
            terminal.println("You have no bookings yet.");
            return;
        }
        TextTable table = new TextTable("Reference", "Status", "Route", "Departure", "Return", "Pax", "Total")
                .alignRight(5, 6);
        for (Booking b : bookings) {
            Flight out = b.outboundFlight();
            table.addRow(b.reference(), b.status(), out.origin().code() + (b.isRoundTrip() ? " <-> " : " -> ")
                            + out.destination().code(), DATE_TIME.format(out.departure()),
                    b.returnFlightOptional().map(f -> DATE_TIME.format(f.departure())).orElse("-"),
                    b.seatCount(), b.totalPrice());
        }
        table.render().forEach(terminal::println);

        String reference = terminal.readLine("Enter a reference to see details (Enter to go back): ").trim();
        if (!reference.isEmpty()) {
            printBookingDetails(bookingService.findBooking(currentUser, reference));
        }
    }

    private void cancelBooking() {
        heading("Cancel a booking");
        String reference = terminal.readLine("Booking reference: ");
        Booking booking = bookingService.findBooking(currentUser, reference);
        printBookingDetails(booking);
        if (booking.status() == BookingStatus.CANCELLED) {
            terminal.println("This booking is already cancelled.");
            return;
        }
        if (readYesNo("Cancel this booking? (y/n): ")) {
            Booking cancelled = bookingService.cancel(currentUser, booking.reference());
            terminal.println("Booking " + cancelled.reference() + " has been cancelled. "
                    + cancelled.totalPrice() + " will be refunded.");
        } else {
            terminal.println("The booking was not changed.");
        }
    }

    private void printBookingDetails(Booking b) {
        terminal.println();
        terminal.println("Booking " + b.reference() + " - " + b.status());
        for (Flight f : b.flights()) {
            terminal.println("  " + f.flightNumber() + "  " + f.origin().label() + "  ->  " + f.destination().label());
            terminal.println("         " + DATE.format(f.departure()) + ", " + TIME.format(f.departure())
                    + " - " + TIME.format(f.arrival()) + " (" + formatDuration(f.duration()) + ", "
                    + f.aircraft().displayName() + ")");
        }
        terminal.println("  Class: " + b.fareClass().displayName()
                + ", checked baggage: " + (b.checkedBaggage() ? "yes" : "no"));
        terminal.println("  Passengers:");
        for (Passenger p : b.passengers()) {
            terminal.println("    - " + p.fullName() + " (" + p.phoneNumber() + ")");
        }
        terminal.println("  Total paid: " + b.totalPrice());
    }


    private Optional<FlightAvailability> chooseFlight(String title, List<FlightAvailability> flights) {
        terminal.println();
        terminal.println(title);
        if (flights.isEmpty()) {
            terminal.println("  No flights available.");
            return Optional.empty();
        }
        List<FlightAvailability> shown = flights.subList(0, Math.min(flights.size(), MAX_ROWS));
        printFlights(shown, true);
        while (true) {
            int choice = readInt("Choose a flight (0 to go back): ", 0, shown.size());
            if (choice == 0) {
                return Optional.empty();
            }
            FlightAvailability selected = shown.get(choice - 1);
            if (selected.availableSeats() > 0) {
                return Optional.of(selected);
            }
            terminal.println("That flight is full. Please choose another one.");
        }
    }

    private void printFlights(List<FlightAvailability> flights, boolean numbered) {
        TextTable table = numbered
                ? new TextTable("#", "Flight", "From", "To", "Departs", "Arrives", "Duration", "Economy", "First",
                "Seats").alignRight(0, 7, 8, 9)
                : new TextTable("Flight", "From", "To", "Departs", "Arrives", "Duration", "Economy", "First",
                "Seats").alignRight(6, 7, 8);
        int n = 1;
        for (FlightAvailability fa : flights) {
            Flight f = fa.flight();
            List<Object> cells = new ArrayList<>(List.of(f.flightNumber(), f.origin().code(), f.destination().code(),
                    DATE_TIME.format(f.departure()), DATE_TIME.format(f.arrival()), formatDuration(f.duration()),
                    f.economyPrice(), f.firstClassPrice(), fa.availableSeats() == 0 ? "FULL" : fa.availableSeats()));
            if (numbered) {
                cells.add(0, n++);
            }
            table.addRow(cells.toArray());
        }
        table.render().forEach(terminal::println);
    }

    private <T> Optional<T> choose(String title, List<T> options, Function<T, String> label) {
        terminal.println();
        terminal.println(title);
        if (options.isEmpty()) {
            terminal.println("  Nothing available.");
            return Optional.empty();
        }
        for (int i = 0; i < options.size(); i++) {
            terminal.println(String.format("  %2d) %s", i + 1, label.apply(options.get(i))));
        }
        terminal.println("   0) Back");
        int choice = readInt("Choose: ", 0, options.size());
        return choice == 0 ? Optional.empty() : Optional.of(options.get(choice - 1));
    }

    private int readInt(String prompt, int min, int max) {
        while (true) {
            String input = terminal.readLine(prompt).trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the error message
            }
            terminal.println("Please enter a number from " + min + " to " + max + ".");
        }
    }

    private boolean readYesNo(String prompt) {
        while (true) {
            String input = terminal.readLine(prompt).trim().toLowerCase(Locale.ROOT);
            if (input.equals("y") || input.equals("yes")) {
                return true;
            }
            if (input.equals("n") || input.equals("no")) {
                return false;
            }
            terminal.println("Please answer y or n.");
        }
    }

    private String readValid(String prompt, Predicate<String> isValid, String error) {
        while (true) {
            String input = terminal.readLine(prompt).trim();
            if (isValid.test(input)) {
                return input;
            }
            terminal.println(error);
        }
    }

    private void banner() {
        String line = "=".repeat(APP_NAME.length() + 8);
        terminal.println(line);
        terminal.println("    " + APP_NAME);
        terminal.println(line);
    }

    private void heading(String title) {
        terminal.println();
        terminal.println("--- " + title + " ---");
    }

    static String formatDuration(Duration d) {
        return d.toHours() + "h " + String.format("%02dm", d.toMinutesPart());
    }
}
