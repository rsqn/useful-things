package tech.rsqn.useful.things.ledger;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
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

        long sequenceId = sequenceCounter.incrementAndGet();
        record.setSequenceId(sequenceId);

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
            return -1;
        }
        lastEnqueuedSeq.set(sequenceId);
        incrementCachedSizeAfterLogicalWrite();

        notifySubscribers(record);

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
        
        // Drain remaining records
        BlockingQueue<T> q = writeQueue;
        while (q != null && !q.isEmpty()) {
            T record = q.poll();
            if (record != null) {
                try {
                    driver.write(record);
                    if (record.getSequenceId() != null) {
                        lastPersistedSeq.updateAndGet(cur -> Math.max(cur, record.getSequenceId()));
                    }
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error draining write queue", e);
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
                    try {
                        driver.write(record);
                        if (record.getSequenceId() != null) {
                            lastPersistedSeq.updateAndGet(cur -> Math.max(cur, record.getSequenceId()));
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
        return status;
    }
}
