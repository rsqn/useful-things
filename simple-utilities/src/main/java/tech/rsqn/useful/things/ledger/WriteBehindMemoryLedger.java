package tech.rsqn.useful.things.ledger;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Memory-first ledger with asynchronous persistence (write-behind).
 */
public class WriteBehindMemoryLedger extends MemoryLedger {
    private final BlockingQueue<BaseEvent> writeQueue = new LinkedBlockingQueue<>();
    private final Thread writerThread;
    private volatile boolean running = true;

    public WriteBehindMemoryLedger(EventType eventType, PersistenceDriver driver, LedgerConfig config, 
                                   Predicate<BaseEvent> retentionFilter, ExecutorService notificationExecutor) {
        super(eventType, driver, config, retentionFilter, notificationExecutor);
        
        this.writerThread = new Thread(this::processWriteQueue, "LedgerWriter-" + eventType.getValue());
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    @Override
    public long write(Map<String, Object> data, Instant timestamp) {
        if (!keepRunning.shouldKeepRunning()) return -1;

        long eventId = sequenceCounter.incrementAndGet();
        Instant ts = timestamp != null ? timestamp : Instant.now();
        BaseEvent event = new BaseEvent(eventType, ts, data, eventId);

        // Add to memory immediately (FAST)
        memory.addLast(event);
        memorySize.incrementAndGet();

        // Queue for persistence
        if (!writeQueue.offer(event)) {
            System.err.println("ERROR: Ledger write queue full for " + eventType.getValue() + ". Event " + eventId + " may be lost from disk.");
        }

        notifySubscribers(event);

        return eventId;
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
        writerThread.interrupt();
        try {
            writerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Drain remaining events
        while (!writeQueue.isEmpty()) {
            BaseEvent event = writeQueue.poll();
            if (event != null) {
                try {
                    driver.write(event);
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
                BaseEvent event = writeQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    try {
                        driver.write(event);
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
