package tech.rsqn.useful.things.ledger;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Memory-first ledger with asynchronous persistence (write-behind).
 *
 * @param <T> The type of record stored.
 */
public class WriteBehindMemoryLedger<T extends Record> extends MemoryLedger<T> {
    private static final Logger LOG = Logger.getLogger(WriteBehindMemoryLedger.class.getName());

    /** Default capacity for the write-behind queue to avoid unbounded memory growth. */
    public static final int DEFAULT_WRITE_QUEUE_CAPACITY = 1_000_000;
    /** Maximum number of queued records combined into one driver append. */
    private final int persistBatchSize = LedgerPersistBatch.resolveBatchSize();

    private int writeQueueCapacity = DEFAULT_WRITE_QUEUE_CAPACITY;
    private volatile BlockingQueue<T> writeQueue;
    private Thread writerThread;
    private volatile boolean running = true;
    private final Object initLock = new Object();

    private final AtomicLong lastEnqueuedSeq = new AtomicLong(0);
    private final AtomicLong lastPersistedSeq = new AtomicLong(0);
    private final Object persistedLock = new Object();

    public WriteBehindMemoryLedger(RecordType recordType, PersistenceDriver<T> driver,
                                   Predicate<T> retentionFilter) {
        super(recordType, driver, retentionFilter);
    }

    /**
     * Sets the maximum number of records waiting for persistence.
     * Must be called before {@link #init()}.
     */
    public void setWriteQueueCapacity(int writeQueueCapacity) {
        if (writeQueueCapacity <= 0) {
            throw new IllegalArgumentException("writeQueueCapacity must be positive");
        }
        synchronized (initLock) {
            if (writeQueue != null) {
                throw new IllegalStateException("setWriteQueueCapacity must be called before init()");
            }
            this.writeQueueCapacity = writeQueueCapacity;
        }
    }

    public int getWriteQueueCapacity() {
        return writeQueueCapacity;
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
        synchronized (initLock) {
            if (writerThread != null) {
                return;
            }
            this.writeQueue = new LinkedBlockingQueue<>(writeQueueCapacity);
            this.writerThread = new Thread(this::processWriteQueue, "LedgerWriter-" + recordType.getValue());
            this.writerThread.setDaemon(true);
            this.writerThread.start();
        }
    }

    private void ensureInitialised() {
        if (writeQueue == null) {
            throw new IllegalStateException("WriteBehindMemoryLedger.init() must be called before use");
        }
    }

    @Override
    public long write(T record) {
        if (!keepRunning.shouldKeepRunning()) return -1;

        long sequenceId = assignSequenceId(record);

        // Add to memory immediately (FAST)
        memory.addLast(record);
        memorySize.incrementAndGet();

        ensureInitialised();

        // Queue for persistence (backpressure, never drop)
        try {
            writeQueue.put(record);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sequenceCounter.decrementAndGet();
            releaseReservedSequenceId(sequenceId);
            return -1;
        }
        lastEnqueuedSeq.set(sequenceId);
        releaseReservedSequenceId(sequenceId);
        incrementCachedSizeAfterLogicalWrite();

        notifySubscribers(record);

        return sequenceId;
    }

    /**
     * Adds a batch to memory immediately and enqueues each record with
     * backpressure. The writer thread combines queued records into driver
     * batch appends when it can do so.
     *
     * @param records records to enqueue; must not be null or contain null
     * @return assigned sequence IDs in record order
     */
    @Override
    public List<Long> writeBatch(List<T> records) {
        if (records == null) {
            throw new IllegalArgumentException("records must not be null");
        }
        if (records.isEmpty()) {
            return List.of();
        }
        if (!keepRunning.shouldKeepRunning()) {
            throw new IllegalStateException("Write-behind memory ledger is no longer accepting writes");
        }

        List<Long> sequenceIds = new ArrayList<>(records.size());
        for (T record : records) {
            if (record == null) {
                throw new IllegalArgumentException("records must not contain null");
            }
            long sequenceId = record.getSequenceId() == null
                    ? assignSequenceId(record)
                    : reservedSequenceId(record);
            memory.addLast(record);
            memorySize.incrementAndGet();
            sequenceIds.add(sequenceId);
        }

        ensureInitialised();
        try {
            for (T record : records) {
                writeQueue.put(record);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while enqueueing memory ledger batch", e);
        }
        lastEnqueuedSeq.set(sequenceIds.get(sequenceIds.size() - 1));
        releaseReservedSequenceIds(sequenceIds);

        for (T record : records) {
            incrementCachedSizeAfterLogicalWrite();
            notifySubscribers(record);
        }
        return sequenceIds;
    }

    private long reservedSequenceId(T record) {
        long sequenceId = record.getSequenceId();
        requireReservedSequenceId(sequenceId);
        return sequenceId;
    }

    @Override
    public void flush() {
        BlockingQueue<T> q = writeQueue;
        if (q == null) {
            super.flush();
            return;
        }

        long target = lastEnqueuedSeq.get();
        if (target > 0) {
            long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
            synchronized (persistedLock) {
                while (lastPersistedSeq.get() < target) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        LOG.log(Level.SEVERE, "Timed out waiting for write-behind persistence to reach seq " + target);
                        break;
                    }
                    try {
                        persistedLock.wait(Math.min(remaining, 1000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        super.flush();
    }

    @Override
    public void close() throws Exception {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Drain remaining records in bounded persistence batches
        BlockingQueue<T> q = writeQueue;
        if (q != null && !q.isEmpty()) {
            List<T> records = new ArrayList<>(persistBatchSize);
            while (q.drainTo(records, persistBatchSize) > 0) {
                try {
                    driver.writeBatch(records);
                    if (!records.isEmpty() && records.get(records.size() - 1).getSequenceId() != null) {
                        lastPersistedSeq.updateAndGet(
                                cur -> Math.max(cur, records.get(records.size() - 1).getSequenceId()));
                    }
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error draining write queue", e);
                } finally {
                    records.clear();
                }
            }
        }
        
        super.close();
    }

    private void processWriteQueue() {
        while (running) {
            try {
                BlockingQueue<T> q = writeQueue;
                if (q == null) {
                    Thread.sleep(10);
                    continue;
                }
                T record = q.poll(1, TimeUnit.SECONDS);
                if (record != null) {
                    List<T> records = new ArrayList<>();
                    records.add(record);
                    q.drainTo(records, persistBatchSize - 1);
                    try {
                        driver.writeBatch(records);
                        T last = records.get(records.size() - 1);
                        if (last.getSequenceId() != null) {
                            lastPersistedSeq.updateAndGet(cur -> Math.max(cur, last.getSequenceId()));
                        }
                        synchronized (persistedLock) {
                            persistedLock.notifyAll();
                        }
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Error processing write queue", e); // Log error
                    }
                }
            } catch (InterruptedException e) {
                if (!running) break;
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = super.healthCheck();
        BlockingQueue<T> q = writeQueue;
        if (q != null) {
            status.put("writeQueueSize", q.size());
            status.put("writeQueueRemainingCapacity", q.remainingCapacity());
            status.put("writeQueueCapacity", writeQueueCapacity);
        }
        status.put("lastEnqueuedSeq", lastEnqueuedSeq.get());
        status.put("lastPersistedSeq", lastPersistedSeq.get());
        status.put("persistBatchSize", persistBatchSize);
        return status;
    }
}
