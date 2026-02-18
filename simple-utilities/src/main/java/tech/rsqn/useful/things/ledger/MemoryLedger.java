package tech.rsqn.useful.things.ledger;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Memory-first ledger implementation.
 * Reads are served from memory only.
 *
 * @param <T> The type of record stored.
 */
public class MemoryLedger<T extends Record> extends AbstractLedger<T> {
    protected final ConcurrentLinkedDeque<T> memory = new ConcurrentLinkedDeque<>();
    protected final AtomicLong memorySize = new AtomicLong(0);
    protected int preferredMaxSize = 10000;
    protected int alarmSize = 100000;
    protected final Predicate<T> retentionFilter;
    
    private final Object housekeepingLock = new Object();
    private volatile long lastAlarmLogTime = 0;
    private static final long ALARM_LOG_INTERVAL_MS = 5000; // 5 seconds

    public MemoryLedger(RecordType recordType, PersistenceDriver<T> driver, 
                        Predicate<T> retentionFilter, ExecutorService notificationExecutor) {
        super(recordType, driver, notificationExecutor);
        this.retentionFilter = retentionFilter;
    }

    public void setPreferredMaxSize(int preferredMaxSize) {
        this.preferredMaxSize = preferredMaxSize;
    }

    public void setAlarmSize(int alarmSize) {
        this.alarmSize = alarmSize;
    }

    @PostConstruct
    public void init() {
        if (preferredMaxSize <= 0) {
            throw new IllegalStateException("preferredMaxSize must be positive");
        }
        hydrate();
    }

    protected void hydrate() {
        // Hydrate from driver
        driver.read(-1, record -> {
            if (retentionFilter == null || retentionFilter.test(record)) {
                memory.addLast(record);
                memorySize.incrementAndGet();
            }
            return true; // Continue
        });
        
        // Ensure sequence counter is up to date (already done in super, but just in case)
        if (!memory.isEmpty()) {
            Long lastId = memory.peekLast().getSequenceId();
            if (lastId != null && lastId > sequenceCounter.get()) {
                sequenceCounter.set(lastId);
            }
        }
    }

    @Override
    public long write(T record) {
        if (!keepRunning.shouldKeepRunning()) return -1;

        long sequenceId = sequenceCounter.incrementAndGet();
        record.setSequenceId(sequenceId);

        // Add to memory
        memory.addLast(record);
        memorySize.incrementAndGet();

        // Write to persistence
        try {
            driver.write(record);
        } catch (IOException e) {
            e.printStackTrace(); // Log error but continue
            // We do NOT rollback memory here (FAST requirement)
        }

        notifySubscribers(record);

        return sequenceId;
    }

    @Override
    public void read(long fromSequence, Predicate<T> filter, ReadCallback<T> callback) {
        Iterator<T> iterator = memory.iterator();
        while (iterator.hasNext()) {
            T record = iterator.next();
            
            if (fromSequence != -1 && record.getSequenceId() != null && record.getSequenceId() <= fromSequence) {
                continue;
            }

            if (filter == null || filter.test(record)) {
                if (!callback.onRecord(record)) {
                    break;
                }
            }
        }
    }

    @Override
    public void readReverse(long fromSequence, Predicate<T> filter, ReadCallback<T> callback) {
        Iterator<T> iterator = memory.descendingIterator();
        while (iterator.hasNext()) {
            T record = iterator.next();

            if (fromSequence != -1 && record.getSequenceId() != null && record.getSequenceId() >= fromSequence) {
                continue;
            }

            if (filter == null || filter.test(record)) {
                if (!callback.onRecord(record)) {
                    break;
                }
            }
        }
    }

    @Override
    public void flush() {
        try {
            driver.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void housekeeping() {
        synchronized (housekeepingLock) {
            // Remove records that don't match retention filter
            if (retentionFilter != null) {
                while (!memory.isEmpty()) {
                    T record = memory.peekFirst();
                    if (record != null && !retentionFilter.test(record)) {
                        memory.pollFirst();
                        memorySize.decrementAndGet();
                    } else {
                        break;
                    }
                }
            }

            // Trim to preferred size
            while (memorySize.get() > preferredMaxSize) {
                memory.pollFirst();
                memorySize.decrementAndGet();
            }
        }

        // Check alarm size
        long currentSize = memorySize.get();
        if (currentSize > alarmSize) {
            long now = System.currentTimeMillis();
            if (now - lastAlarmLogTime > ALARM_LOG_INTERVAL_MS) {
                System.err.println("ALARM: Memory ledger size " + currentSize + " exceeds alarm size " + alarmSize);
                lastAlarmLogTime = now;
            }
        }
    }
    
    @Override
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = super.healthCheck();
        status.put("memorySize", memorySize.get());
        status.put("preferredMaxSize", preferredMaxSize);
        status.put("alarmSize", alarmSize);
        return status;
    }
}
