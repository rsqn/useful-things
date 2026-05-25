package tech.rsqn.useful.things.ledger;

import tech.rsqn.useful.things.apps.KeepRunning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Predicate;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for ledgers.
 * <p>
 * Notification uses a lock-free {@link ConcurrentLinkedQueue} with dedicated consumer threads.
 * Producers never block on enqueue (CAS-only). Consumers park when the queue is empty and are
 * unparked by the producer, eliminating the lock contention inherent in BlockingQueue-based
 * executors.
 *
 * @param <T> The type of record stored.
 */
public abstract class AbstractLedger<T extends Record> implements Ledger<T> {

    /** Default number of consumer threads for notification dispatch. */
    public static final int DEFAULT_NOTIFICATION_CORE_POOL_SIZE = 2;

    /** Default maximum pool size (kept for API compat — consumers fixed at core size). */
    public static final int DEFAULT_NOTIFICATION_MAX_POOL_SIZE = 10;

    /** Default capacity — unused with ConcurrentLinkedQueue (unbounded) but kept for API compat. */
    public static final int DEFAULT_NOTIFICATION_QUEUE_CAPACITY = 1000;

    /** Default keep-alive — unused but kept for API compat. */
    public static final long DEFAULT_NOTIFICATION_KEEP_ALIVE_SECONDS = 60;

    private static final Logger LOG = Logger.getLogger(AbstractLedger.class.getName());
    protected final RecordType recordType;
    protected final PersistenceDriver<T> driver;
    protected final AtomicLong sequenceCounter = new AtomicLong(0);
    protected final KeepRunning keepRunning = new KeepRunning();

    private int notificationCorePoolSize = DEFAULT_NOTIFICATION_CORE_POOL_SIZE;
    private int notificationMaxPoolSize = DEFAULT_NOTIFICATION_MAX_POOL_SIZE;
    private int notificationQueueCapacity = DEFAULT_NOTIFICATION_QUEUE_CAPACITY;
    private long notificationKeepAliveSeconds = DEFAULT_NOTIFICATION_KEEP_ALIVE_SECONDS;

    private final Object subscriberLock = new Object();
    private final List<SubscriberRecord<T>> subscribers = new ArrayList<>();
    private volatile List<SubscriberRecord<T>> subscriberSnapshot = List.of();

    private final ConcurrentLinkedQueue<Runnable> notificationQueue = new ConcurrentLinkedQueue<>();
    private volatile Thread[] consumerThreads;
    private volatile boolean consumersRunning = false;
    private final Object consumerInitLock = new Object();

    protected volatile boolean started = false;

    public AbstractLedger(RecordType recordType, PersistenceDriver<T> driver) {
        this.recordType = recordType;
        this.driver = driver;
        recoverSequenceId();
        this.started = true;
    }

    public void setNotificationCorePoolSize(int notificationCorePoolSize) {
        this.notificationCorePoolSize = notificationCorePoolSize;
    }

    public void setNotificationMaxPoolSize(int notificationMaxPoolSize) {
        this.notificationMaxPoolSize = notificationMaxPoolSize;
    }

    public void setNotificationQueueCapacity(int notificationQueueCapacity) {
        this.notificationQueueCapacity = notificationQueueCapacity;
    }

    public void setNotificationKeepAliveSeconds(long notificationKeepAliveSeconds) {
        this.notificationKeepAliveSeconds = notificationKeepAliveSeconds;
    }

    private void ensureConsumersStarted() {
        if (consumerThreads == null) {
            synchronized (consumerInitLock) {
                if (consumerThreads == null) {
                    consumersRunning = true;
                    int threadCount = notificationCorePoolSize;
                    consumerThreads = new Thread[threadCount];
                    for (int i = 0; i < threadCount; i++) {
                        Thread t = new Thread(this::consumerLoop, "ledger-notify-" + recordType.getValue());
                        t.setDaemon(true);
                        t.start();
                        consumerThreads[i] = t;
                    }
                }
            }
        }
    }

    private void consumerLoop() {
        while (consumersRunning) {
            Runnable task = notificationQueue.poll();
            if (task != null) {
                try {
                    task.run();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error in notification consumer", e);
                }
                // Batch drain — process all available before parking
                while ((task = notificationQueue.poll()) != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error in notification consumer", e);
                    }
                }
            } else {
                LockSupport.parkNanos(1_000L); // 1µs — busy-spin tradeoff for throughput
            }
        }
        // Drain remaining on shutdown
        Runnable task;
        while ((task = notificationQueue.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error in notification consumer during shutdown", e);
            }
        }
    }

    private void recoverSequenceId() {
        driver.readReverse(-1, record -> {
            if (record != null && record.getSequenceId() != null) {
                sequenceCounter.set(record.getSequenceId());
            }
            return false;
        });
    }

    protected void beforeDriverClose() throws Exception {
    }

    @Override
    public void close() throws Exception {
        keepRunning.stopRunning();
        flush();
        beforeDriverClose();
        driver.close();
        consumersRunning = false;
        Thread[] threads = consumerThreads;
        if (threads != null) {
            for (Thread t : threads) {
                if (t != null) {
                    LockSupport.unpark(t);
                    t.join(5000);
                }
            }
        }
    }

    @Override
    public void subscribe(Predicate<T> filter, Consumer<T> subscriber) {
        synchronized (subscriberLock) {
            subscribers.add(new SubscriberRecord<>(subscriber, filter));
            subscriberSnapshot = List.copyOf(subscribers);
        }
    }

    protected void notifySubscribers(T record) {
        List<SubscriberRecord<T>> snapshot = subscriberSnapshot;
        if (snapshot.isEmpty()) {
            return;
        }
        boolean anyMatch = false;
        for (SubscriberRecord<T> sub : snapshot) {
            if (sub.filter == null || sub.filter.test(record)) {
                anyMatch = true;
                break;
            }
        }
        if (!anyMatch) {
            return;
        }

        ensureConsumersStarted();
        T recordRef = record;
        notificationQueue.offer(() -> dispatchNotifySubscribers(recordRef, snapshot));
    }

    private void dispatchNotifySubscribers(T record, List<SubscriberRecord<T>> snapshot) {
        for (SubscriberRecord<T> sub : snapshot) {
            if (sub.filter == null || sub.filter.test(record)) {
                try {
                    sub.subscriber.accept(record);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error notifying subscriber", e);
                }
            }
        }
    }

    @Override
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("recordType", recordType.getValue());
        status.put("started", started);
        status.put("sequenceCounter", sequenceCounter.get());
        status.put("notificationQueueSize", notificationQueue.size());
        synchronized (subscriberLock) {
            status.put("subscriberCount", subscribers.size());
        }
        return status;
    }

    private static class SubscriberRecord<T> {
        final Consumer<T> subscriber;
        final Predicate<T> filter;

        SubscriberRecord(Consumer<T> subscriber, Predicate<T> filter) {
            this.subscriber = subscriber;
            this.filter = filter;
        }
    }
}
