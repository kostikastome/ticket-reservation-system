package com.ticketreservation.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Line-based input/output. Abstracted so the console UI can be tested with scripted input.
 */
public interface Terminal {

    /** Prints {@code prompt} and reads one line, or throws {@link EndOfInputException} at end of input. */
    String readLine(String prompt);

    /** Like {@link #readLine} but does not echo the input when a real console is available. */
    char[] readPassword(String prompt);

    void println(String line);

    default void println() {
        println("");
    }

    /** Thrown when the input stream is closed (e.g. Ctrl+D), so the app can exit cleanly. */
    final class EndOfInputException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EndOfInputException() {
            super("End of input");
        }
    }

    static Terminal system() {
        Console console = System.console();
        Charset charset = console != null ? console.charset() : Charset.defaultCharset();
        return new StreamTerminal(new BufferedReader(new InputStreamReader(System.in, charset)), System.out, console);
    }

    final class StreamTerminal implements Terminal {

        private final BufferedReader in;
        private final PrintStream out;
        private final Console console;

        public StreamTerminal(BufferedReader in, PrintStream out) {
            this(in, out, null);
        }

        StreamTerminal(BufferedReader in, PrintStream out, Console console) {
            this.in = Objects.requireNonNull(in);
            this.out = Objects.requireNonNull(out);
            this.console = console;
        }

        @Override
        public String readLine(String prompt) {
            if (console != null) {
                // Read through the same Console as passwords so no input is buffered elsewhere.
                String line = console.readLine("%s", prompt);
                if (line == null) {
                    throw new EndOfInputException();
                }
                return line;
            }
            out.print(prompt);
            out.flush();
            try {
                String line = in.readLine();
                if (line == null) {
                    throw new EndOfInputException();
                }
                return line;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public char[] readPassword(String prompt) {
            if (console != null) {
                char[] password = console.readPassword("%s", prompt);
                if (password == null) {
                    throw new EndOfInputException();
                }
                return password;
            }
            return readLine(prompt).toCharArray();
        }

        @Override
        public void println(String line) {
            out.println(line);
        }
    }
}
