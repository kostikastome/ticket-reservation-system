package com.ticketreservation.repository;

import com.ticketreservation.repository.memory.InMemoryRepositories;

/** Runs the repository contract against the in-memory implementation. */
class InMemoryRepositoryTest extends RepositoryContractTest {

    @Override
    protected Repositories createRepositories() {
        return InMemoryRepositories.create();
    }
}
