package tech.rsqn.useful.things.ledger;

/**
 * Terminal failure while persisting records on a write-behind ledger writer thread.
 * After this is raised, the ledger stops accepting new writes.
 */
public final class LedgerPersistException extends RuntimeException {

    public LedgerPersistException(String message, Throwable cause) {
        super(message, cause);
    }
}
