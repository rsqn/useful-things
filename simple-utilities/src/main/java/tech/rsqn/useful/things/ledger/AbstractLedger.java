package tech.rsqn.useful.things.ledger;

import tech.rsqn.useful.things.apps.KeepRunning;

import java.io.IOException;
import java.time.Instant;
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
 */
public abstract class AbstractLedger implements Ledger {
    protected final EventType eventType;
    protected final PersistenceDriver driver;
    protected final AtomicLong sequenceCounter = new AtomicLong(0);
    protected final KeepRunning keepRunning = new KeepRunning();
    protected final ExecutorService notificationExecutor;
    
    private final Object subscriberLock = new Object();
    private final List<SubscriberRecord> subscribers = new ArrayList<>();
    
    protected volatile boolean started = false;

    public AbstractLedger(EventType eventType, PersistenceDriver driver, ExecutorService notificationExecutor) {
        this.eventType = eventType;
        this.driver = driver;
        this.notificationExecutor = notificationExecutor != null ? notificationExecutor : Executors.newCachedThreadPool();
        
        // Startup sequence ID recovery
        recoverSequenceId();
        this.started = true;
    }

    private void recoverSequenceId() {
        // Read the last event to determine the sequence counter
        driver.readReverse(-1, event -> {
            if (event != null && event.getEventId() != null) {
                sequenceCounter.set(event.getEventId());
            }
            return false; // Stop after first event (which is the last one)
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
    public void subscribe(Consumer<BaseEvent> subscriber, Predicate<BaseEvent> filter) {
        synchronized (subscriberLock) {
            subscribers.add(new SubscriberRecord(subscriber, filter));
        }
    }

    protected void notifySubscribers(BaseEvent event) {
        List<SubscriberRecord> snapshot;
        synchronized (subscriberLock) {
            snapshot = new ArrayList<>(subscribers);
        }

        if (snapshot.isEmpty()) return;

        if (notificationExecutor != null && !notificationExecutor.isShutdown()) {
            notificationExecutor.submit(() -> doNotify(snapshot, event));
        } else {
            doNotify(snapshot, event);
        }
    }

    private void doNotify(List<SubscriberRecord> subs, BaseEvent event) {
        for (SubscriberRecord sub : subs) {
            try {
                if (sub.filter == null || sub.filter.test(event)) {
                    sub.subscriber.accept(event);
                }
            } catch (Exception e) {
                e.printStackTrace(); // Log error but don't stop notification
            }
        }
    }

    @Override
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("eventType", eventType.getValue());
        status.put("started", started);
        status.put("sequenceCounter", sequenceCounter.get());
        synchronized (subscriberLock) {
            status.put("subscriberCount", subscribers.size());
        }
        return status;
    }

    // Inner class to track subscriber state
    private static class SubscriberRecord {
        final Consumer<BaseEvent> subscriber;
        final Predicate<BaseEvent> filter;

        SubscriberRecord(Consumer<BaseEvent> subscriber, Predicate<BaseEvent> filter) {
            this.subscriber = subscriber;
            this.filter = filter;
        }
    }
}
