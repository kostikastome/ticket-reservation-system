package com.ticketreservation.domain;

public enum Aircraft {
    BOEING_737("Boeing 737", 150),
    AIRBUS_A320("Airbus A320", 180),
    BOEING_747("Boeing 747", 400);

    private final String displayName;
    private final int capacity;

    Aircraft(String displayName, int capacity) {
        this.displayName = displayName;
        this.capacity = capacity;
    }

    public String displayName() {
        return displayName;
    }

    public int capacity() {
        return capacity;
    }
}
