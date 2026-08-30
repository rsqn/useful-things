package tech.rsqn.useful.things.ledger;

import java.io.IOException;
import java.util.List;

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
     * Writes a batch of records in order.
     * <p>
     * Implementations may optimize the physical append. The default preserves
     * correctness for drivers that only support single-record writes.
     *
     * @param records records to write in sequence order
     * @throws IOException if any write fails
     */
    default void writeBatch(List<T> records) throws IOException {
        if (records == null) {
            throw new IllegalArgumentException("records must not be null");
        }
        for (T record : records) {
            write(record);
        }
    }

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
     * Returns the number of logical lines (e.g. JSONL rows) in the persistence layer.
     * Implementations may use a fast newline scan rather than parsing records.
     * Default -1 if not supported.
     *
     * @return line count, or -1 if not supported / on error
     */
    default long count() {
        return -1L;
    }
}
