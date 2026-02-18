package tech.rsqn.useful.things.ledger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Disk-only ledger implementation.
 * Reads and writes directly to the persistence driver. No in-memory cache.
 * Reads ALL records from disk on each read/readReverse call.
 * Size is established on first size() call (line count) and then maintained on writes.
 *
 * @param <T> The type of record stored.
 */
public class DiskLedger<T extends Record> extends AbstractLedger<T> {

    private final AtomicLong cachedSize = new AtomicLong(-1);
    private final Object sizeLock = new Object();

    public DiskLedger(RecordType recordType, PersistenceDriver<T> driver, ExecutorService notificationExecutor) {
        super(recordType, driver, notificationExecutor);
    }

    @Override
    public long write(T record) {
        if (!keepRunning.shouldKeepRunning()) return -1;

        long sequenceId = sequenceCounter.incrementAndGet();
        record.setSequenceId(sequenceId);

        try {
            driver.write(record);
        } catch (IOException e) {
            e.printStackTrace();
        }

        synchronized (sizeLock) {
            if (cachedSize.get() >= 0) {
                cachedSize.incrementAndGet();
            }
        }

        notifySubscribers(record);
        return sequenceId;
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
            e.printStackTrace();
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
