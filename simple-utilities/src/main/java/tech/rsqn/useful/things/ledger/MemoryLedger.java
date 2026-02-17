package tech.rsqn.useful.things.ledger;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Memory-first ledger implementation.
 * Reads are served from memory only.
 */
public class MemoryLedger extends AbstractLedger {
    protected final ConcurrentLinkedDeque<BaseEvent> memory = new ConcurrentLinkedDeque<>();
    protected final AtomicLong memorySize = new AtomicLong(0);
    protected final int preferredMaxSize;
    protected final int alarmSize;
    protected final Predicate<BaseEvent> retentionFilter;
    
    private final Object housekeepingLock = new Object();
    private volatile long lastAlarmLogTime = 0;
    private static final long ALARM_LOG_INTERVAL_MS = 5000; // 5 seconds

    public MemoryLedger(EventType eventType, PersistenceDriver driver, LedgerConfig config, 
                        Predicate<BaseEvent> retentionFilter, ExecutorService notificationExecutor) {
        super(eventType, driver, notificationExecutor);
        this.retentionFilter = retentionFilter;
        this.preferredMaxSize = config.getInt("ledger.memory.preferred_max_size", 10000);
        this.alarmSize = config.getInt("ledger.memory.alarm_size", 100000);
        
        hydrate();
    }

    protected void hydrate() {
        // Hydrate from driver
        driver.read(-1, event -> {
            if (retentionFilter == null || retentionFilter.test(event)) {
                memory.addLast(event);
                memorySize.incrementAndGet();
            }
            return true; // Continue
        });
        
        // Ensure sequence counter is up to date (already done in super, but just in case)
        if (!memory.isEmpty()) {
            long lastId = memory.peekLast().getEventId();
            if (lastId > sequenceCounter.get()) {
                sequenceCounter.set(lastId);
            }
        }
    }

    @Override
    public long write(Map<String, Object> data, Instant timestamp) {
        if (!keepRunning.shouldKeepRunning()) return -1;

        long eventId = sequenceCounter.incrementAndGet();
        Instant ts = timestamp != null ? timestamp : Instant.now();
        BaseEvent event = new BaseEvent(eventType, ts, data, eventId);

        // Add to memory
        memory.addLast(event);
        memorySize.incrementAndGet();

        // Write to persistence
        try {
            driver.write(event);
        } catch (IOException e) {
            e.printStackTrace(); // Log error but continue
            // We do NOT rollback memory here (FAST requirement)
        }

        notifySubscribers(event);

        return eventId;
    }

    @Override
    public void read(long fromSequence, Predicate<BaseEvent> filter, ReadCallback<BaseEvent> callback) {
        Iterator<BaseEvent> iterator = memory.iterator();
        while (iterator.hasNext()) {
            BaseEvent event = iterator.next();
            
            if (fromSequence != -1 && event.getEventId() <= fromSequence) {
                continue;
            }

            if (filter == null || filter.test(event)) {
                if (!callback.onEvent(event)) {
                    break;
                }
            }
        }
    }

    @Override
    public void readReverse(long fromSequence, Predicate<BaseEvent> filter, ReadCallback<BaseEvent> callback) {
        Iterator<BaseEvent> iterator = memory.descendingIterator();
        while (iterator.hasNext()) {
            BaseEvent event = iterator.next();

            if (fromSequence != -1 && event.getEventId() >= fromSequence) {
                continue;
            }

            if (filter == null || filter.test(event)) {
                if (!callback.onEvent(event)) {
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
            // Remove events that don't match retention filter
            if (retentionFilter != null) {
                while (!memory.isEmpty()) {
                    BaseEvent event = memory.peekFirst();
                    if (event != null && !retentionFilter.test(event)) {
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
