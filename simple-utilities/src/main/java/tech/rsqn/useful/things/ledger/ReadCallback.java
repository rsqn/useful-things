package tech.rsqn.useful.things.ledger;

/**
 * Functional interface for reading events from the ledger.
 *
 * @param <T> The type of event.
 */
@FunctionalInterface
public interface ReadCallback<T> {
    /**
     * Called for each event read.
     *
     * @param event The event read.
     * @return true to continue iterating, false to stop.
     */
    boolean onEvent(T event);
}
