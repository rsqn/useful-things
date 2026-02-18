package tech.rsqn.useful.things.ledger;

import java.io.IOException;

/**
 * Interface for persistence drivers.
 *
 * @param <T> The type of record stored.
 */
public interface PersistenceDriver<T extends Record> extends AutoCloseable {
    /**
     * Writes a record to the persistence layer.
     *
     * @param record The record to write.
     * @throws IOException If the write fails.
     */
    void write(T record) throws IOException;

    /**
     * Reads records from the persistence layer forward.
     *
     * @param fromSequence The sequence ID to start reading from (-1 means from the beginning).
     * @param callback     Callback for each record (returns true to continue, false to stop).
     */
    void read(long fromSequence, ReadCallback<T> callback);

    /**
     * Reads records from the persistence layer in reverse.
     *
     * @param fromSequence The sequence ID to start reading from (-1 means from the end).
     * @param callback     Callback for each record (returns true to continue, false to stop).
     */
    void readReverse(long fromSequence, ReadCallback<T> callback);

    /**
     * Flushes any buffered writes to persistence.
     *
     * @throws IOException If the flush fails.
     */
    void flush() throws IOException;

    /**
     * Returns the number of records in the persistence layer.
     * Default -1 if not supported.
     *
     * @return record count, or -1 if not supported
     */
    default long count() {
        return -1L;
    }
}
