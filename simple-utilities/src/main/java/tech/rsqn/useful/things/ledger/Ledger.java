package tech.rsqn.useful.things.ledger;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Interface for the ledger system.
 *
 * @param <T> The type of record stored in this ledger.
 */
public interface Ledger<T extends Record> extends AutoCloseable {
    /**
     * Writes a record to the ledger.
     *
     * @param record The record to write.
     * @return The sequence ID of the record.
     */
    long write(T record);

    /**
     * Reads records from the ledger forward.
     *
     * @param fromSequence The sequence ID to start reading from (-1 means from the beginning).
     * @param filter       Optional filter (null means no filtering).
     * @param callback     Callback for each record (returns true to continue, false to stop).
     */
    void read(long fromSequence, Predicate<T> filter, ReadCallback<T> callback);

    /**
     * Reads records from the ledger in reverse.
     *
     * @param fromSequence The sequence ID to start reading from (-1 means from the end).
     * @param filter       Optional filter (null means no filtering).
     * @param callback     Callback for each record (returns true to continue, false to stop).
     */
    void readReverse(long fromSequence, Predicate<T> filter, ReadCallback<T> callback);

    /**
     * Subscribes to new records.
     *
     * @param subscriber The subscriber to notify.
     * @param filter     Optional filter (null means receive all records).
     */
    void subscribe(Consumer<T> subscriber, Predicate<T> filter);

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
