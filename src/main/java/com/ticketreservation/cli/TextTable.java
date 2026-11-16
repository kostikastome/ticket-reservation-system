package com.ticketreservation.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Renders rows as a plain-text table with aligned columns.
 */
final class TextTable {

    private final String[] headers;
    private final boolean[] rightAligned;
    private final List<String[]> rows = new ArrayList<>();

    TextTable(String... headers) {
        this.headers = headers.clone();
        this.rightAligned = new boolean[headers.length];
    }

    /** Right-aligns the given columns (useful for numbers and prices). */
    TextTable alignRight(int... columns) {
        for (int column : columns) {
            rightAligned[column] = true;
        }
        return this;
    }

    TextTable addRow(Object... cells) {
        if (cells.length != headers.length) {
            throw new IllegalArgumentException("Expected " + headers.length + " cells but got " + cells.length);
        }
        rows.add(Arrays.stream(cells).map(String::valueOf).toArray(String[]::new));
        return this;
    }

    List<String> render() {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        List<String> lines = new ArrayList<>(rows.size() + 2);
        lines.add(format(headers, widths));
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            separator.append(i == 0 ? "" : "  ").append("-".repeat(widths[i]));
        }
        lines.add(separator.toString());
        for (String[] row : rows) {
            lines.add(format(row, widths));
        }
        return lines;
    }

    private String format(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append("  ");
            }
            String pad = " ".repeat(widths[i] - cells[i].length());
            sb.append(rightAligned[i] ? pad + cells[i] : cells[i] + pad);
        }
        return sb.toString().stripTrailing();
    }
}
