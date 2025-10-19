package banking.persistence.repository;

import banking.snapshot.BankSnapshot;

import java.util.Optional;

/**
 * Abstraction over the storage layer for persisting and retrieving {@link BankSnapshot} data.
 */
public interface BankRepository {

    /**
     * Loads the most recent persisted bank snapshot if one is available.
     *
     * @return an optional snapshot representing the stored state
     */
    Optional<BankSnapshot> load();

    /**
     * Persists the provided snapshot, replacing any previously stored state.
     *
     * @param snapshot the immutable bank snapshot to persist
     */
    void save(BankSnapshot snapshot);
}
