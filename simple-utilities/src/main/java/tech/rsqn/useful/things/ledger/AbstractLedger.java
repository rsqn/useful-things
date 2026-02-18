package tech.rsqn.useful.things.ledger;

import tech.rsqn.useful.things.apps.KeepRunning;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Abstract base class for ledgers.
 *
 * @param <T> The type of record stored.
 */
public abstract class AbstractLedger<T extends Record> implements Ledger<T> {
    protected final RecordType recordType;
    protected final PersistenceDriver<T> driver;
    protected final AtomicLong sequenceCounter = new AtomicLong(0);
    protected final KeepRunning keepRunning = new KeepRunning();
    protected final ExecutorService notificationExecutor;
    
    private final Object subscriberLock = new Object();
    private final List<SubscriberRecord<T>> subscribers = new ArrayList<>();
    
    protected volatile boolean started = false;

    public AbstractLedger(RecordType recordType, PersistenceDriver<T> driver, ExecutorService notificationExecutor) {
        this.recordType = recordType;
        this.driver = driver;
        this.notificationExecutor = notificationExecutor != null ? notificationExecutor : Executors.newCachedThreadPool();
        
        // Startup sequence ID recovery
        recoverSequenceId();
        this.started = true;
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
        if (notificationExecutor != null && !notificationExecutor.isShutdown()) {
            notificationExecutor.shutdown();
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

        if (notificationExecutor != null && !notificationExecutor.isShutdown()) {
            notificationExecutor.submit(() -> doNotify(snapshot, record));
        } else {
            doNotify(snapshot, record);
        }
    }

    private void doNotify(List<SubscriberRecord<T>> subs, T record) {
        for (SubscriberRecord<T> sub : subs) {
            try {
                if (sub.filter == null || sub.filter.test(record)) {
                    sub.subscriber.accept(record);
                }
            } catch (Exception e) {
                e.printStackTrace(); // Log error but don't stop notification
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
