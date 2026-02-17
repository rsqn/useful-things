package tech.rsqn.useful.things.ledger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Real-time event stream reader for the ledger system.
 */
public class StreamReader {
    private final LedgerRegistry registry;
    private final Map<EventType, List<Consumer<BaseEvent>>> subscribers = new ConcurrentHashMap<>();

    public StreamReader(LedgerRegistry registry) {
        this.registry = registry;
    }

    public void subscribe(EventType eventType, Consumer<BaseEvent> callback) {
        subscribers.computeIfAbsent(eventType, k -> {
            List<Consumer<BaseEvent>> list = Collections.synchronizedList(new ArrayList<>());
            // Subscribe to the underlying ledger once per type, and dispatch to all local subscribers
            Ledger ledger = registry.getLedger(eventType);
            if (ledger != null) {
                ledger.subscribe(this::notifySubscribers, null);
            }
            return list;
        }).add(callback);
    }

    public void unsubscribe(EventType eventType, Consumer<BaseEvent> callback) {
        List<Consumer<BaseEvent>> list = subscribers.get(eventType);
        if (list != null) {
            list.remove(callback);
            if (list.isEmpty()) {
                subscribers.remove(eventType);
                // Note: We don't unsubscribe from the underlying ledger because Ledger interface doesn't support unsubscribe yet
            }
        }
    }

    private void notifySubscribers(BaseEvent event) {
        List<Consumer<BaseEvent>> list = subscribers.get(event.getEventType());
        if (list == null || list.isEmpty()) return;
        
        synchronized (list) {
            for (Consumer<BaseEvent> callback : list) {
                try {
                    callback.accept(event);
                } catch (Exception e) {
                    // Ignore subscriber errors
                }
            }
        }
    }

    /**
     * Tails events from a ledger, optionally following new events.
     * Uses a callback to process events.
     * 
     * @param eventType The event type to tail.
     * @param follow    Whether to follow new events (tail -f).
     * @param callback  Callback for each event. Returns true to continue, false to stop.
     */
    public void tailEvents(EventType eventType, boolean follow, ReadCallback<BaseEvent> callback) {
        Ledger ledger = registry.getLedger(eventType);
        if (ledger == null) {
            return;
        }

        if (!follow) {
            ledger.read(-1, null, callback);
            return;
        }

        // For following, we need to subscribe first to capture events while reading history
        // We use a wrapper callback that checks the return value of the user callback
        // If user callback returns false, we need to stop everything.
        
        // This is tricky because 'read' is blocking (history), but 'subscribe' is async (live).
        // If we subscribe, we get live events on a different thread.
        // We need to coordinate.
        
        // Strategy:
        // 1. Subscribe with a listener that pushes to the user callback.
        // 2. Read history.
        // 3. If history read stops (callback returns false), we unsubscribe and return.
        // 4. If history read finishes, we continue with live events (already subscribed).
        
        // BUT: Duplicate handling.
        // If we subscribe first, we might get an event via subscription that we also read from history.
        // We need to track max ID seen in history.
        
        // Also, if 'read' blocks, and 'subscribe' calls callback on another thread,
        // we have concurrent calls to 'callback'. Is 'callback' thread-safe?
        // Usually callbacks should be thread-safe or we should serialize.
        // Let's assume we need to serialize.
        
        // Actually, the user asked for "iterate", implying sequential processing.
        // If we have history + live, we usually want sequential: history then live.
        // But live events arrive asynchronously.
        // So we probably need a queue to serialize them if we want strict ordering and single-threaded callback.
        // But 'tailEvents' with 'follow' usually blocks the caller thread?
        // Or does it return and let the callback happen async?
        // If it blocks, it acts like a server loop.
        
        // Let's implement a blocking loop that reads history then drains a queue of live events.
        
        // Queue for live events
        java.util.concurrent.BlockingQueue<BaseEvent> liveQueue = new java.util.concurrent.LinkedBlockingQueue<>();
        Consumer<BaseEvent> listener = liveQueue::offer;
        
        // Subscribe first
        ledger.subscribe(listener, null);
        
        // Track max ID
        java.util.concurrent.atomic.AtomicLong maxId = new java.util.concurrent.atomic.AtomicLong(-1);
        
        // Read history
        // We wrap the callback to update maxId
        boolean[] keepGoing = {true};
        ledger.read(-1, null, event -> {
            if (event.getEventId() != null) {
                maxId.accumulateAndGet(event.getEventId(), Math::max);
            }
            boolean result = callback.onEvent(event);
            keepGoing[0] = result;
            return result;
        });
        
        if (!keepGoing[0]) {
            // User stopped during history
            // Unsubscribe? Ledger doesn't support unsubscribe yet.
            // We just stop processing.
            return;
        }
        
        // Process live events
        try {
            while (true) {
                BaseEvent event = liveQueue.take();
                if (event.getEventId() != null && event.getEventId() <= maxId.get()) {
                    continue; // Skip duplicate
                }
                
                if (!callback.onEvent(event)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
