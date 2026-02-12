package tech.rsqn.useful.things.ledger;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Real-time event stream reader for the ledger system.
 */
public class StreamReader {
    private final LedgerRegistry registry;
    private final Map<EventType, List<Consumer<BaseEvent>>> subscribers = new HashMap<>();

    public StreamReader(LedgerRegistry registry) {
        this.registry = registry;
    }

    public void subscribe(EventType eventType, Consumer<BaseEvent> callback) {
        synchronized (subscribers) {
            subscribers.computeIfAbsent(eventType, k -> {
                List<Consumer<BaseEvent>> list = new ArrayList<>();
                // Subscribe to the underlying ledger once per type, and dispatch to all local subscribers
                EventLedger ledger = registry.getLedger(eventType);
                if (ledger != null) {
                    ledger.subscribe(this::notifySubscribers);
                }
                return list;
            }).add(callback);
        }
    }

    public void unsubscribe(EventType eventType, Consumer<BaseEvent> callback) {
        synchronized (subscribers) {
            List<Consumer<BaseEvent>> list = subscribers.get(eventType);
            if (list != null) {
                list.remove(callback);
                if (list.isEmpty()) {
                    subscribers.remove(eventType);
                    // Note: We don't unsubscribe from the underlying ledger because EventLedger doesn't support unsubscribe yet
                }
            }
        }
    }

    private void notifySubscribers(BaseEvent event) {
        List<Consumer<BaseEvent>> callbacks;
        synchronized (subscribers) {
            List<Consumer<BaseEvent>> list = subscribers.get(event.getEventType());
            if (list == null || list.isEmpty()) return;
            callbacks = new ArrayList<>(list);
        }
        for (Consumer<BaseEvent> callback : callbacks) {
            try {
                callback.accept(event);
            } catch (Exception e) {
                // Ignore subscriber errors
            }
        }
    }

    /**
     * Tails events from a ledger file, optionally following new events.
     * Returns a Stream that must be closed to release resources (especially for 'follow' mode).
     */
    public Stream<BaseEvent> tailEvents(EventType eventType, boolean follow) {
        EventLedger ledger = registry.getLedger(eventType);
        if (ledger == null) {
            return Stream.empty();
        }

        if (!follow) {
            return ledger.readEvents(null);
        }

        // For following, we need to subscribe first to capture events while reading history
        BlockingQueue<BaseEvent> liveQueue = new LinkedBlockingQueue<>();
        Consumer<BaseEvent> listener = liveQueue::offer;
        ledger.subscribe(listener);

        // Read history
        Stream<BaseEvent> historyStream = ledger.readEvents(null);
        
        // Track max ID to deduplicate
        AtomicLong maxId = new AtomicLong(-1);
        
        Stream<BaseEvent> trackedHistory = historyStream.peek(e -> {
            if (e.getEventId() != null) {
                maxId.accumulateAndGet(e.getEventId(), Math::max);
            }
        });

        // Create live stream from queue
        Iterator<BaseEvent> liveIterator = new Iterator<BaseEvent>() {
            @Override
            public boolean hasNext() {
                return true; // Infinite stream
            }

            @Override
            public BaseEvent next() {
                try {
                    while (true) {
                        BaseEvent e = liveQueue.take();
                        if (e.getEventId() != null && e.getEventId() <= maxId.get()) {
                            continue; // Skip duplicate
                        }
                        return e;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        };

        Stream<BaseEvent> liveStream = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(liveIterator, Spliterator.ORDERED), 
                false);

        // Concatenate history and live
        return Stream.concat(trackedHistory, liveStream)
                .onClose(() -> {
                    // We can't easily unsubscribe from EventLedger as it doesn't support unsubscribe yet
                    // But we can stop the liveQueue from growing if we had a way to remove the listener
                    // For now, we accept the listener stays attached. 
                    // Ideally EventLedger should support unsubscribe.
                });
    }
}
