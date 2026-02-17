package tech.rsqn.useful.things.ledger;

import java.io.IOException;

/**
 * Interface for persistence drivers.
 */
public interface PersistenceDriver extends AutoCloseable {
    /**
     * Writes an event to the persistence layer.
     *
     * @param event The event to write.
     * @throws IOException If the write fails.
     */
    void write(BaseEvent event) throws IOException;

    /**
     * Reads events from the persistence layer forward.
     *
     * @param fromSequence The sequence ID to start reading from (-1 means from the beginning).
     * @param callback     Callback for each event (returns true to continue, false to stop).
     */
    void read(long fromSequence, ReadCallback<BaseEvent> callback);

    /**
     * Reads events from the persistence layer in reverse.
     *
     * @param fromSequence The sequence ID to start reading from (-1 means from the end).
     * @param callback     Callback for each event (returns true to continue, false to stop).
     */
    void readReverse(long fromSequence, ReadCallback<BaseEvent> callback);

    /**
     * Flushes any buffered writes to persistence.
     *
     * @throws IOException If the flush fails.
     */
    void flush() throws IOException;
}
