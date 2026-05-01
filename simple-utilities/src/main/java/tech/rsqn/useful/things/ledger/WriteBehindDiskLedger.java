package tech.rsqn.useful.things.ledger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Disk-backed ledger that assigns a sequence id, enqueues the record for persistence,
 * notifies subscribers on the caller thread (before the record is durably written),
 * then returns. Gson serialization and file I/O run on a dedicated writer thread.
 * <p>
 * The write queue is bounded; {@link #write} uses {@link BlockingQueue#put} so producers
 * block when the queue is full until the writer makes space.
 * <p>
 * Call {@link #init()} after constructing the ledger and starting the persistence driver
 * (same pattern as {@link WriteBehindMemoryLedger}).
 *
 * @param <T> The type of record stored.
 */
public class WriteBehindDiskLedger<T extends Record> extends DiskLedger<T> {
    private static final Logger LOG = Logger.getLogger(WriteBehindDiskLedger.class.getName());

    /** Default capacity of the bounded queue between caller and the writer thread. */
    public static final int DEFAULT_WRITE_QUEUE_CAPACITY = 100_000;

    private interface QueueItem {
    }

    /**
     * User record waiting for the writer thread (static holder so the queue type is not tied to the outer type parameter).
     */
    private static final class DataItem implements QueueItem {
        private final Record record;

        private DataItem(Record record) {
            this.record = record;
        }
    }

    /** Barrier: writer flushes the driver then releases {@code done}. */
    private static final class FlushItem implements QueueItem {
        private final CountDownLatch done;

        private FlushItem(CountDownLatch done) {
            this.done = done;
        }
    }

    /** Tells the writer thread to finish and drain any stragglers. */
    private enum ShutdownItem implements QueueItem {
        INSTANCE
    }

    private int writeQueueCapacity = DEFAULT_WRITE_QUEUE_CAPACITY;
    private volatile BlockingQueue<QueueItem> queue;
    private volatile Thread writerThread;
    private final Object initLock = new Object();

    public WriteBehindDiskLedger(RecordType recordType, PersistenceDriver<T> driver) {
        super(recordType, driver);
    }

    /**
     * Sets the maximum number of records waiting for disk persistence. Must be called before {@link #init()}.
     */
    public void setWriteQueueCapacity(int writeQueueCapacity) {
        if (writeQueueCapacity <= 0) {
            throw new IllegalArgumentException("writeQueueCapacity must be positive");
        }
        synchronized (initLock) {
            if (queue != null) {
                throw new IllegalStateException("setWriteQueueCapacity must be called before init()");
            }
            this.writeQueueCapacity = writeQueueCapacity;
        }
    }

    public int getWriteQueueCapacity() {
        return writeQueueCapacity;
    }

    /**
     * Starts the background writer thread. Idempotent.
     */
    public void init() {
        synchronized (initLock) {
            if (writerThread != null) {
                return;
            }
            queue = new LinkedBlockingQueue<>(writeQueueCapacity);
            writerThread = new Thread(this::writerLoop, "LedgerWriteBehindDisk-" + recordType.getValue());
            writerThread.setDaemon(true);
            writerThread.start();
        }
    }

    private void ensureInitialised() {
        if (queue == null) {
            throw new IllegalStateException("WriteBehindDiskLedger.init() must be called before use");
        }
    }

    @Override
    public long write(T record) {
        if (!keepRunning.shouldKeepRunning()) {
            return -1;
        }

        long sequenceId = sequenceCounter.incrementAndGet();
        record.setSequenceId(sequenceId);

        ensureInitialised();
        try {
            queue.put(new DataItem((Record) record));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sequenceCounter.decrementAndGet();
            return -1;
        }

        incrementCachedSizeAfterLogicalWrite();
        notifySubscribers(record);
        return sequenceId;
    }

    private void writerLoop() {
        try {
            while (true) {
                QueueItem item = queue.take();
                if (handleQueueItem(item)) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // If take() was interrupted, a FlushItem may still be queued — drain so flush() cannot hang.
            drainQueueFullyOnWriter();
        }
    }

    /**
     * @return true if the writer thread should exit
     */
    private boolean handleQueueItem(QueueItem item) {
        if (item instanceof ShutdownItem) {
            drainTailAfterShutdown();
            return true;
        }
        dispatchDataOrFlush(item);
        return false;
    }

    private void dispatchDataOrFlush(QueueItem item) {
        if (item instanceof FlushItem flushItem) {
            flushDriverQuietly();
            flushItem.done.countDown();
            return;
        }
        if (item instanceof DataItem dataItem) {
            try {
                @SuppressWarnings("unchecked")
                T toWrite = (T) dataItem.record;
                driver.write(toWrite);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Error writing to disk ledger (write-behind)", e);
            }
        }
    }

    /**
     * After a shutdown marker was taken, persist any trailing items (and honour flush barriers).
     */
    private void drainTailAfterShutdown() {
        QueueItem p;
        while ((p = queue.poll()) != null) {
            if (p instanceof ShutdownItem) {
                continue;
            }
            dispatchDataOrFlush(p);
        }
    }

    private void drainQueueFullyOnWriter() {
        QueueItem p;
        while ((p = queue.poll()) != null) {
            if (p instanceof ShutdownItem) {
                drainTailAfterShutdown();
                return;
            }
            dispatchDataOrFlush(p);
        }
    }

    private void flushDriverQuietly() {
        try {
            driver.flush();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error flushing disk ledger (write-behind)", e);
        }
    }

    @Override
    public void flush() {
        if (queue == null) {
            super.flush();
            return;
        }
        // Second close() or flush() after the writer has exited would block on put(Flush) forever.
        if (writerThread != null && !writerThread.isAlive()) {
            drainQueueFullyOnWriter();
            super.flush();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        try {
            queue.put(new FlushItem(latch));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                LOG.log(Level.SEVERE, "Timed out waiting for write-behind flush to complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        super.flush();
    }

    @Override
    protected void beforeDriverClose() throws Exception {
        if (writerThread == null) {
            super.beforeDriverClose();
            return;
        }
        try {
            queue.put(ShutdownItem.INSTANCE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writerThread.interrupt();
        }
        writerThread.join(TimeUnit.MINUTES.toMillis(1));
        if (writerThread.isAlive()) {
            LOG.log(Level.WARNING, "Write-behind disk ledger writer thread did not finish within timeout; interrupting");
            writerThread.interrupt();
            writerThread.join(TimeUnit.SECONDS.toMillis(10));
        }
        if (writerThread.isAlive()) {
            LOG.log(Level.SEVERE, "Write-behind disk ledger writer thread still alive after interrupt");
        }
        super.beforeDriverClose();
    }

    @Override
    public void read(long fromSequence, Predicate<T> filter, ReadCallback<T> callback) {
        if (queue != null) {
            flush();
        }
        super.read(fromSequence, filter, callback);
    }

    @Override
    public void readReverse(long fromSequence, Predicate<T> filter, ReadCallback<T> callback) {
        if (queue != null) {
            flush();
        }
        super.read(fromSequence, filter, callback);
    }

    @Override
    public long size() {
        if (queue != null) {
            flush();
        }
        return super.size();
    }

    @Override
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = super.healthCheck();
        BlockingQueue<QueueItem> q = queue;
        if (q != null) {
            status.put("writeQueueSize", q.size());
            status.put("writeQueueRemainingCapacity", q.remainingCapacity());
            status.put("writeQueueCapacity", writeQueueCapacity);
        }
        return status;
    }
}
