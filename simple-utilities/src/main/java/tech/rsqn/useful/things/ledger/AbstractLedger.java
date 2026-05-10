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
 * <p>
 * Notification uses a bounded {@link ThreadPoolExecutor}. When the pool and queue are saturated,
 * the default {@code AbortPolicy} would drop work with {@link java.util.concurrent.RejectedExecutionException};
 * this type installs a handler that {@linkplain java.util.concurrent.BlockingQueue#put blocks on enqueue}
 * instead, so producers wait for capacity while subscribers still run only on pool threads (never
 * {@code CallerRunsPolicy} on the writer).
 * <p>
 * For each written record, the ledger enqueues <strong>one</strong> notification task that invokes
 * every matching subscriber <strong>serially</strong> in snapshot (subscription) order on a single
 * pool thread. Different records may still be processed concurrently across pool threads.
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
    /** Immutable snapshot for lock-free reads on the notify path; rebuilt on {@link #subscribe}. */
    private volatile List<SubscriberRecord<T>> subscriberSnapshot = List.of();
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
                            threadFactory,
                            (r, ex) -> {
                                if (ex.isShutdown()) {
                                    return;
                                }
                                try {
                                    ex.getQueue().put(r);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
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

    /**
     * Hook invoked after {@link #flush()} and before {@link PersistenceDriver#close()} during {@link #close()}.
     * Subclasses with asynchronous persistence may join background writers here.
     */
    protected void beforeDriverClose() throws Exception {
    }

    @Override
    public void close() throws Exception {
        keepRunning.stopRunning();
        flush();
        beforeDriverClose();
        driver.close();
        ExecutorService exec = notificationExecutor;
        if (exec != null && !exec.isShutdown()) {
            exec.shutdown();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Thread-safe: updates the published subscriber snapshot used by {@link #notifySubscribers}.
     */
    @Override
    public void subscribe(Predicate<T> filter, Consumer<T> subscriber) {
        synchronized (subscriberLock) {
            subscribers.add(new SubscriberRecord<>(subscriber, filter));
            subscriberSnapshot = List.copyOf(subscribers);
        }
    }

    /**
     * Notifies subscribers for {@code record} using one {@link ExecutorService#execute} per record
     * when at least one subscriber matches. Matching subscribers run serially on a pool thread in
     * snapshot order; exceptions in one subscriber do not skip later subscribers.
     */
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

        ExecutorService executor = getOrCreateNotificationExecutor();
        T recordRef = record;
        executor.execute(() -> dispatchNotifySubscribers(recordRef, snapshot));
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
