package tech.rsqn.useful.things.ledger;

import tech.rsqn.useful.things.apps.KeepRunning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for ledgers.
 *
 * @param <T> The type of record stored.
 */
public abstract class AbstractLedger<T extends Record> implements Ledger<T> {

    /** Default core pool size for the notification executor. */
    public static final int DEFAULT_NOTIFICATION_CORE_POOL_SIZE = 2;

    /** Default maximum pool size for the notification executor. */
    public static final int DEFAULT_NOTIFICATION_MAX_POOL_SIZE = 10;

    /** Default capacity of the notification executor's work queue. */
    public static final int DEFAULT_NOTIFICATION_QUEUE_CAPACITY = 1000;

    /** Default keep-alive time in seconds for idle threads beyond the core pool size. */
    public static final long DEFAULT_NOTIFICATION_KEEP_ALIVE_SECONDS = 60;

    private static final Logger LOG = Logger.getLogger(AbstractLedger.class.getName());
    protected final RecordType recordType;
    protected final PersistenceDriver<T> driver;
    protected final AtomicLong sequenceCounter = new AtomicLong(0);
    protected final KeepRunning keepRunning = new KeepRunning();
    private volatile ExecutorService notificationExecutor;

    private int notificationCorePoolSize = DEFAULT_NOTIFICATION_CORE_POOL_SIZE;
    private int notificationMaxPoolSize = DEFAULT_NOTIFICATION_MAX_POOL_SIZE;
    private int notificationQueueCapacity = DEFAULT_NOTIFICATION_QUEUE_CAPACITY;
    private long notificationKeepAliveSeconds = DEFAULT_NOTIFICATION_KEEP_ALIVE_SECONDS;

    private final Object subscriberLock = new Object();
    private final List<SubscriberRecord<T>> subscribers = new ArrayList<>();
    private final Object executorLock = new Object();

    protected volatile boolean started = false;

    public AbstractLedger(RecordType recordType, PersistenceDriver<T> driver) {
        this.recordType = recordType;
        this.driver = driver;

        // Startup sequence ID recovery
        recoverSequenceId();
        this.started = true;
    }

    /**
     * Sets the core pool size for the notification executor. Must be called before the first write.
     */
    public void setNotificationCorePoolSize(int notificationCorePoolSize) {
        this.notificationCorePoolSize = notificationCorePoolSize;
    }

    /**
     * Sets the maximum pool size for the notification executor. Must be called before the first write.
     */
    public void setNotificationMaxPoolSize(int notificationMaxPoolSize) {
        this.notificationMaxPoolSize = notificationMaxPoolSize;
    }

    /**
     * Sets the work queue capacity for the notification executor. Must be called before the first write.
     */
    public void setNotificationQueueCapacity(int notificationQueueCapacity) {
        this.notificationQueueCapacity = notificationQueueCapacity;
    }

    /**
     * Sets the keep-alive time in seconds for idle threads beyond the core pool size. Must be called before the first write.
     */
    public void setNotificationKeepAliveSeconds(long notificationKeepAliveSeconds) {
        this.notificationKeepAliveSeconds = notificationKeepAliveSeconds;
    }

    private ExecutorService getOrCreateNotificationExecutor() {
        if (notificationExecutor == null) {
            synchronized (executorLock) {
                if (notificationExecutor == null) {
                    ThreadFactory threadFactory = r -> new Thread(r, "ledger-notify-" + recordType.getValue());
                    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(notificationQueueCapacity);
                    ThreadPoolExecutor executor = new ThreadPoolExecutor(
                            notificationCorePoolSize,
                            notificationMaxPoolSize,
                            notificationKeepAliveSeconds,
                            TimeUnit.SECONDS,
                            queue,
                            threadFactory);
                    executor.allowCoreThreadTimeOut(true);
                    this.notificationExecutor = executor;
                }
            }
        }
        return notificationExecutor;
    }

    private void recoverSequenceId() {
        // Read the last record to determine the sequence counter
        driver.readReverse(-1, record -> {
            if (record != null && record.getSequenceId() != null) {
                sequenceCounter.set(record.getSequenceId());
            }
            return false; // Stop after first record (which is the last one)
        });
    }

    @Override
    public void close() throws Exception {
        keepRunning.stopRunning();
        flush();
        driver.close();
        ExecutorService exec = notificationExecutor;
        if (exec != null && !exec.isShutdown()) {
            exec.shutdown();
        }
    }

    @Override
    public void subscribe(Predicate<T> filter, Consumer<T> subscriber) {
        synchronized (subscriberLock) {
            subscribers.add(new SubscriberRecord<>(subscriber, filter));
        }
    }

    protected void notifySubscribers(T record) {
        List<SubscriberRecord<T>> snapshot;
        synchronized (subscriberLock) {
            snapshot = new ArrayList<>(subscribers);
        }

        if (snapshot.isEmpty()) return;

        ExecutorService executor = getOrCreateNotificationExecutor();
        for (SubscriberRecord<T> sub : snapshot) {
            if (sub.filter == null || sub.filter.test(record)) {
                SubscriberRecord<T> subRef = sub;
                executor.submit(() -> {
                    try {
                        subRef.subscriber.accept(record);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error notifying subscriber", e);
                    }
                });
            }
        }
    }

    @Override
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("recordType", recordType.getValue());
        status.put("started", started);
        status.put("sequenceCounter", sequenceCounter.get());
        synchronized (subscriberLock) {
            status.put("subscriberCount", subscribers.size());
        }
        return status;
    }

    // Inner class to track subscriber state
    private static class SubscriberRecord<T> {
        final Consumer<T> subscriber;
        final Predicate<T> filter;

        SubscriberRecord(Consumer<T> subscriber, Predicate<T> filter) {
            this.subscriber = subscriber;
            this.filter = filter;
        }
    }
}
