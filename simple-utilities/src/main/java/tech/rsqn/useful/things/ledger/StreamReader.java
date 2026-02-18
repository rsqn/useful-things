package tech.rsqn.useful.things.ledger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Real-time record stream reader for the ledger system.
 */
public class StreamReader {
    private final LedgerRegistry registry;
    private final Map<RecordType, List<Consumer<Record>>> subscribers = new ConcurrentHashMap<>();

    public StreamReader(LedgerRegistry registry) {
        this.registry = registry;
    }

    public void subscribe(RecordType recordType, Consumer<Record> callback) {
        subscribers.computeIfAbsent(recordType, k -> {
            List<Consumer<Record>> list = Collections.synchronizedList(new ArrayList<>());
            // Subscribe to the underlying ledger once per type, and dispatch to all local subscribers
            Ledger<Record> ledger = (Ledger<Record>) registry.getLedger(recordType);
            if (ledger != null) {
                // We cast to Ledger<Record> to subscribe with a generic Consumer<Record>
                // This works because any T extends Record, so Consumer<Record> can consume it.
                ledger.subscribe(this::notifySubscribers, null);
            }
            return list;
        }).add(callback);
    }

    public void unsubscribe(RecordType recordType, Consumer<Record> callback) {
        List<Consumer<Record>> list = subscribers.get(recordType);
        if (list != null) {
            list.remove(callback);
            if (list.isEmpty()) {
                subscribers.remove(recordType);
                // Note: We don't unsubscribe from the underlying ledger because Ledger interface doesn't support unsubscribe yet
            }
        }
    }

    private void notifySubscribers(Record record) {
        List<Consumer<Record>> list = subscribers.get(record.getType());
        if (list == null || list.isEmpty()) return;
        
        synchronized (list) {
            for (Consumer<Record> callback : list) {
                try {
                    callback.accept(record);
                } catch (Exception e) {
                    // Ignore subscriber errors
                }
            }
        }
    }

    /**
     * Tails records from a ledger, optionally following new records.
     * Uses a callback to process records.
     * 
     * @param recordType The record type to tail.
     * @param follow    Whether to follow new records (tail -f).
     * @param callback  Callback for each record. Returns true to continue, false to stop.
     */
    public void tailEvents(RecordType recordType, boolean follow, ReadCallback<Record> callback) {
        Ledger<Record> ledger = (Ledger<Record>) registry.getLedger(recordType);
        if (ledger == null) {
            return;
        }

        if (!follow) {
            ledger.read(-1, null, callback);
            return;
        }

        // For following, we need to subscribe first to capture records while reading history
        
        // Queue for live records
        java.util.concurrent.BlockingQueue<Record> liveQueue = new java.util.concurrent.LinkedBlockingQueue<>();
        Consumer<Record> listener = liveQueue::offer;
        
        // Subscribe first
        ledger.subscribe(listener, null);
        
        // Track max ID
        java.util.concurrent.atomic.AtomicLong maxId = new java.util.concurrent.atomic.AtomicLong(-1);
        
        // Read history
        // We wrap the callback to update maxId
        boolean[] keepGoing = {true};
        ledger.read(-1, null, (ReadCallback<Record>) record -> {
            if (record.getSequenceId() != null) {
                maxId.accumulateAndGet(record.getSequenceId(), Math::max);
            }
            boolean result = callback.onRecord(record);
            keepGoing[0] = result;
            return result;
        });
        
        if (!keepGoing[0]) {
            // User stopped during history
            return;
        }
        
        // Process live records
        try {
            while (true) {
                Record record = liveQueue.take();
                if (record.getSequenceId() != null && record.getSequenceId() <= maxId.get()) {
                    continue; // Skip duplicate
                }
                
                if (!callback.onRecord(record)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
