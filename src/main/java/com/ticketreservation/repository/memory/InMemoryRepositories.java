package com.ticketreservation.repository.memory;

import com.ticketreservation.repository.Repositories;

/**
 * Creates in-memory repositories that share one store. Data is lost when the program exits.
 */
public final class InMemoryRepositories {

    private InMemoryRepositories() {
    }

    public static Repositories create() {
        InMemoryStore store = new InMemoryStore();
        return new Repositories(
                new InMemoryFlightRepository(store),
                new InMemoryBookingRepository(store),
                new InMemoryUserRepository(store));
    }
}
