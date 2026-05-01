package tech.rsqn.useful.things.ledger;

import java.io.IOException;
import java.util.Map;
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

        long sequenceId = sequenceCounter.incrementAndGet();
        record.setSequenceId(sequenceId);

        try {
            driver.write(record);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error writing to disk ledger", e);
        }

        incrementCachedSizeAfterLogicalWrite();

        notifySubscribers(record);
        return sequenceId;
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
