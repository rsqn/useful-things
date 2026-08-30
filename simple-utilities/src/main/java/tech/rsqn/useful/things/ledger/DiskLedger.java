package tech.rsqn.useful.things.ledger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Disk-only ledger implementation.
 * Reads and writes directly to the persistence driver. No in-memory cache.
 * Reads ALL records from disk on each read/readReverse call.
 * <p>
 * Logical JSONL line count is loaded once in the constructor (fast newline scan via
 * {@link PersistenceDriver#count()}) and then updated on each {@link #write}; {@link #size()}
 * reads the cached value.
 *
 * @param <T> The type of record stored.
 */
public class DiskLedger<T extends Record> extends AbstractLedger<T> {
    private static final Logger LOG = Logger.getLogger(DiskLedger.class.getName());

    private final AtomicLong cachedSize = new AtomicLong(-1);
    private final Set<Long> reservedSequenceIds = ConcurrentHashMap.newKeySet();
    private final Object sizeLock = new Object();

    public DiskLedger(RecordType recordType, PersistenceDriver<T> driver) {
        super(recordType, driver);
        long lineCount = driver.count();
        if (lineCount >= 0) {
            cachedSize.set(lineCount);
        }
    }

    /**
     * Cached logical line count on disk (JSONL rows), or {@code -1} if initial {@link PersistenceDriver#count()} failed.
     * For {@link MemoryLedger}, {@link MemoryLedger#size()} is the in-memory (possibly filtered) count instead.
     */
    protected long getDiskLogicalLineCount() {
        return cachedSize.get();
    }

    @Override
    public long write(T record) {
        if (!keepRunning.shouldKeepRunning()) return -1;

        long sequenceId = assignSequenceId(record);

        try {
            driver.write(record);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error writing to disk ledger", e);
        }
        reservedSequenceIds.remove(sequenceId);

        incrementCachedSizeAfterLogicalWrite();

        notifySubscribers(record);
        return sequenceId;
    }

    /**
     * Reserves the next sequence ID for a record without persisting or notifying.
     * This supports callers that assemble a batch before submitting it.
     *
     * @param record record to assign; must not be null
     * @return the reserved sequence ID
     */
    public long assignSequenceId(T record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        if (!keepRunning.shouldKeepRunning()) {
            throw new IllegalStateException("Disk ledger is no longer accepting writes");
        }
        long sequenceId = sequenceCounter.incrementAndGet();
        record.setSequenceId(sequenceId);
        reservedSequenceIds.add(sequenceId);
        return sequenceId;
    }

    protected void releaseReservedSequenceId(long sequenceId) {
        reservedSequenceIds.remove(sequenceId);
    }

    protected void releaseReservedSequenceIds(List<Long> sequenceIds) {
        reservedSequenceIds.removeAll(sequenceIds);
    }

    protected void requireReservedSequenceId(long sequenceId) {
        if (!reservedSequenceIds.contains(sequenceId)) {
            throw new IllegalArgumentException(
                    "Preassigned sequence ID was not reserved by this ledger: " + sequenceId);
        }
    }

    /**
     * Synchronously persists a batch in sequence order, then notifies subscribers
     * in that same order. The records remain individual JSONL rows on disk.
     *
     * @param records records to append; must not be null
     * @return the assigned sequence IDs in record order
     * @throws IllegalArgumentException if records or a record is null
     * @throws IllegalStateException if the ledger is no longer accepting writes
     * @throws UncheckedIOException if persistence fails
     */
    public List<Long> writeBatch(List<T> records) {
        if (records == null) {
            throw new IllegalArgumentException("records must not be null");
        }
        if (records.isEmpty()) {
            return List.of();
        }
        if (!keepRunning.shouldKeepRunning()) {
            throw new IllegalStateException("Disk ledger is no longer accepting writes");
        }

        List<Long> sequenceIds = new ArrayList<>(records.size());
        for (T record : records) {
            if (record == null) {
                throw new IllegalArgumentException("records must not contain null");
            }
            if (record.getSequenceId() == null) {
                sequenceIds.add(assignSequenceId(record));
            } else {
                long sequenceId = record.getSequenceId();
                requireReservedSequenceId(sequenceId);
                sequenceIds.add(sequenceId);
            }
        }

        try {
            driver.writeBatch(records);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write disk ledger batch", e);
        }
        releaseReservedSequenceIds(sequenceIds);

        for (T record : records) {
            incrementCachedSizeAfterLogicalWrite();
            notifySubscribers(record);
        }
        return sequenceIds;
    }

    /**
     * Increments the cached logical size when it is already initialised (non-negative).
     * Used by synchronous {@link #write} and by {@link WriteBehindDiskLedger} after a record is accepted into the queue.
     */
    protected void incrementCachedSizeAfterLogicalWrite() {
        synchronized (sizeLock) {
            if (cachedSize.get() >= 0) {
                cachedSize.incrementAndGet();
            }
        }
    }

    @Override
    public void read(long fromSequence, Predicate<T> filter, ReadCallback<T> callback) {
        driver.read(fromSequence, record -> {
            if (filter != null && !filter.test(record)) {
                return true;
            }
            return callback.onRecord(record);
        });
    }

    @Override
    public void readReverse(long fromSequence, Predicate<T> filter, ReadCallback<T> callback) {
        driver.readReverse(fromSequence, record -> {
            if (filter != null && !filter.test(record)) {
                return true;
            }
            return callback.onRecord(record);
        });
    }

    @Override
    public void flush() {
        try {
            driver.flush();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error flushing disk ledger", e);
        }
    }

    @Override
    public long size() {
        if (cachedSize.get() >= 0) {
            return cachedSize.get();
        }
        synchronized (sizeLock) {
            if (cachedSize.get() >= 0) {
                return cachedSize.get();
            }
            long count = driver.count();
            if (count >= 0) {
                cachedSize.set(count);
            }
            return count;
        }
    }

    /**
     * No-op for disk ledger. Kept for API compatibility.
     */
    public void housekeeping() {
    }

    @Override
    public Map<String, Object> healthCheck() {
        return super.healthCheck();
    }
}
