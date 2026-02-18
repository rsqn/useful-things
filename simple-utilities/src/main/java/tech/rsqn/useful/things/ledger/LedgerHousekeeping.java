package tech.rsqn.useful.things.ledger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Housekeeping tasks for the ledger system.
 */
public class LedgerHousekeeping {

    public static int flushAllLedgers(LedgerRegistry registry) {
        int flushedCount = 0;
        for (Ledger<?> ledger : registry.getAllLedgers()) {
            ledger.flush();
            flushedCount++;
        }
        return flushedCount;
    }

    public static Thread startHousekeepingThread(LedgerRegistry registry, int intervalMinutes) {
        // Default retention: 25 hours
        return startHousekeepingThread(registry, intervalMinutes, 25);
    }

    public static Thread startHousekeepingThread(LedgerRegistry registry, int intervalMinutes, int retentionHours) {
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

                    if (now - lastCleanup >= cleanupIntervalMs) {
                        // Cleanup memory
                        for (Ledger<?> ledger : registry.getAllLedgers()) {
                            if (ledger instanceof MemoryLedger) {
                                ((MemoryLedger<?>) ledger).housekeeping();
                            }
                        }
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
