package tech.rsqn.useful.things.ledger;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Interface for the ledger system.
 */
public interface Ledger extends AutoCloseable {
    /**
     * Writes an event to the ledger.
     *
     * @param data      The data to write.
     * @param timestamp The timestamp of the event.
     * @return The sequence ID of the event.
     */
    long write(Map<String, Object> data, Instant timestamp);

    /**
     * Reads events from the ledger forward.
     *
     * @param fromSequence The sequence ID to start reading from (-1 means from the beginning).
     * @param filter       Optional filter (null means no filtering).
     * @param callback     Callback for each event (returns true to continue, false to stop).
     */
    void read(long fromSequence, Predicate<BaseEvent> filter, ReadCallback<BaseEvent> callback);

    /**
     * Reads events from the ledger in reverse.
     *
     * @param fromSequence The sequence ID to start reading from (-1 means from the end).
     * @param filter       Optional filter (null means no filtering).
     * @param callback     Callback for each event (returns true to continue, false to stop).
     */
    void readReverse(long fromSequence, Predicate<BaseEvent> filter, ReadCallback<BaseEvent> callback);

    /**
     * Subscribes to new events.
     *
     * @param subscriber The subscriber to notify.
     * @param filter     Optional filter (null means receive all events).
     */
    void subscribe(Consumer<BaseEvent> subscriber, Predicate<BaseEvent> filter);

    /**
     * Flushes any buffered writes to persistence.
     */
    void flush();

    /**
     * Checks the health of the ledger.
     *
     * @return A map of health metrics.
     */
    Map<String, Object> healthCheck();
}
