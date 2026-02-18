package tech.rsqn.useful.things.ledger;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Memory-first ledger with asynchronous persistence (write-behind).
 *
 * @param <T> The type of record stored.
 */
public class WriteBehindMemoryLedger<T extends Record> extends MemoryLedger<T> {
    private final BlockingQueue<T> writeQueue = new LinkedBlockingQueue<>();
    private Thread writerThread;
    private volatile boolean running = true;

    public WriteBehindMemoryLedger(RecordType recordType, PersistenceDriver<T> driver, 
                                   Predicate<T> retentionFilter, ExecutorService notificationExecutor) {
        super(recordType, driver, retentionFilter, notificationExecutor);
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
        this.writerThread = new Thread(this::processWriteQueue, "LedgerWriter-" + recordType.getValue());
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    @Override
    public long write(T record) {
        if (!keepRunning.shouldKeepRunning()) return -1;

        long sequenceId = sequenceCounter.incrementAndGet();
        record.setSequenceId(sequenceId);

        // Add to memory immediately (FAST)
        memory.addLast(record);
        memorySize.incrementAndGet();

        // Queue for persistence
        if (!writeQueue.offer(record)) {
            System.err.println("ERROR: Ledger write queue full for " + recordType.getValue() + ". Record " + sequenceId + " may be lost from disk.");
        }

        notifySubscribers(record);

        return sequenceId;
    }

    @Override
    public void flush() {
        // Drain queue to driver
        while (!writeQueue.isEmpty()) {
            try {
                // Wait briefly for queue to drain
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
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
        while (!writeQueue.isEmpty()) {
            T record = writeQueue.poll();
            if (record != null) {
                try {
                    driver.write(record);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        super.close();
    }

    private void processWriteQueue() {
        while (running) {
            try {
                T record = writeQueue.poll(1, TimeUnit.SECONDS);
                if (record != null) {
                    try {
                        driver.write(record);
                    } catch (IOException e) {
                        e.printStackTrace(); // Log error
                    }
                }
            } catch (InterruptedException e) {
                if (!running) break;
                Thread.currentThread().interrupt();
            }
        }
    }
}
