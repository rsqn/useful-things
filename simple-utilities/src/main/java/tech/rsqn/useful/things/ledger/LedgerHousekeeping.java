package tech.rsqn.useful.things.ledger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Housekeeping tasks for the ledger system.
 */
public class LedgerHousekeeping {

    public static int flushAllLedgers(LedgerRegistry registry) {
        int flushedCount = 0;
        for (EventLedger ledger : registry.getAllLedgers()) {
            // We use forceFlush() which only flushes if dirty
            ledger.forceFlush();
            // We don't have a return value from forceFlush to know if it actually flushed
            // But we can assume it did its job.
            // If we want to count actual flushes, we'd need to change EventLedger API.
            // For now, we just count how many ledgers we attempted to flush.
            flushedCount++;
        }
        return flushedCount;
    }

    public static Thread startHousekeepingThread(LedgerRegistry registry, int intervalMinutes) {
        AtomicBoolean running = new AtomicBoolean(true);
        Thread thread = new Thread(() -> {
            long flushIntervalMs = 60 * 1000; // Flush every 60 seconds
            long cleanupIntervalMs = intervalMinutes * 60 * 1000L;
            
            long lastFlush = System.currentTimeMillis();
            long lastCleanup = System.currentTimeMillis();

            while (running.get()) {
                try {
                    long now = System.currentTimeMillis();

                    if (now - lastFlush >= flushIntervalMs) {
                        flushAllLedgers(registry);
                        lastFlush = now;
                    }

                    // We don't have a cleanup method in LedgerRegistry or EventLedger that takes "hours to keep"
                    // The Python code had cleanup_old_price_data.
                    // EventLedger has cleanupMemory(cutoffTime).
                    // We can implement cleanup here.
                    
                    if (now - lastCleanup >= cleanupIntervalMs) {
                        // Cleanup logic would go here if we had a policy
                        // For example, keep 24 hours in memory
                        // Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
                        // for (EventLedger ledger : registry.getAllLedgers()) {
                        //     ledger.cleanupMemory(cutoff);
                        // }
                        lastCleanup = now;
                    }

                    Thread.sleep(1000); // Check every second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running.set(false);
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        Thread.sleep(60000); // Wait on error
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        running.set(false);
                    }
                }
            }
        }, "LedgerHousekeeping");
        
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
