package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LedgerPerformanceTest extends LedgerTestBase {

    private static final int EVENT_COUNT_HIGH = 1_000_000;
    private static final int EVENT_COUNT_LOW = 100_000; // For slower disk ops

    @Test
    public void testAsyncWriteThroughput() throws IOException {
        // Use WriteBehindMemoryLedger for performance
        ledger = createWriteBehindLedger();
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < EVENT_COUNT_HIGH; i++) {
            ledger.write(new TestRecord(Instant.now(), "data", i));
        }
        
        // Wait for flush to ensure persistence
        ledger.flush();
        
        long end = System.currentTimeMillis();
        long duration = end - start;
        double throughput = (double) EVENT_COUNT_HIGH / (duration / 1000.0);
        
        System.out.printf("Async Write Throughput: %.2f ops/sec (%d events in %d ms)%n", throughput, EVENT_COUNT_HIGH, duration);
        
        Assert.assertTrue(throughput > 50_000, "Async write throughput too low: " + throughput);
    }

    @Test
    public void testSyncDiskWriteThroughput() throws IOException {
        // Use standard MemoryLedger which writes synchronously to disk
        // Ensure auto-flush is ON for strict sync test
        ledgerRegistry.setDefaultAutoFlush(true);
        ledger = createLedger();
        
        // Use fewer events for sync disk write as it is much slower (fsync per write)
        int count = 10_000; 
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < count; i++) {
            ledger.write(new TestRecord(Instant.now(), "data", i));
        }
        
        long end = System.currentTimeMillis();
        long duration = end - start;
        double throughput = (double) count / (duration / 1000.0);
        
        System.out.printf("Sync Disk Write Throughput (AutoFlush=true): %.2f ops/sec (%d events in %d ms)%n", throughput, count, duration);
        
        // Expect at least 100 ops/sec (fsync is slow)
        Assert.assertTrue(throughput > 100, "Sync disk write throughput too low: " + throughput);
    }

    @Test
    public void testBufferedDiskWriteThroughput() throws IOException {
        // Use standard MemoryLedger but with buffering
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(1000);
        ledger = createLedger();
        
        int count = EVENT_COUNT_HIGH;
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < count; i++) {
            ledger.write(new TestRecord(Instant.now(), "data", i));
        }
        ledger.flush();
        
        long end = System.currentTimeMillis();
        long duration = end - start;
        double throughput = (double) count / (duration / 1000.0);
        
        System.out.printf("Buffered Disk Write Throughput (AutoFlush=false): %.2f ops/sec (%d events in %d ms)%n", throughput, count, duration);
        
        Assert.assertTrue(throughput > 10_000, "Buffered disk write throughput too low: " + throughput);
    }

    @Test
    public void testMemoryReadThroughput() throws IOException {
        ledger = createWriteBehindLedger();
        
        // Pre-fill
        for (int i = 0; i < EVENT_COUNT_HIGH; i++) {
            ledger.write(new TestRecord(Instant.now(), "data", i));
        }
        
        long start = System.currentTimeMillis();
        
        AtomicInteger readCount = new AtomicInteger(0);
        ledger.read(-1, null, event -> {
            readCount.incrementAndGet();
            return true;
        });
        
        long end = System.currentTimeMillis();
        long duration = end - start;
        double throughput = (double) EVENT_COUNT_HIGH / (duration / 1000.0);
        
        System.out.printf("Memory Read Throughput: %.2f ops/sec (%d events in %d ms)%n", throughput, EVENT_COUNT_HIGH, duration);
        
        Assert.assertEquals(readCount.get(), EVENT_COUNT_HIGH);
        Assert.assertTrue(throughput > 100_000, "Memory read throughput too low: " + throughput);
    }

    @Test
    public void testMemoryReadReverseThroughput() throws IOException {
        ledger = createWriteBehindLedger();
        
        // Pre-fill
        for (int i = 0; i < EVENT_COUNT_HIGH; i++) {
            ledger.write(new TestRecord(Instant.now(), "data", i));
        }
        
        long start = System.currentTimeMillis();
        
        AtomicInteger readCount = new AtomicInteger(0);
        ledger.readReverse(-1, null, event -> {
            readCount.incrementAndGet();
            return true;
        });
        
        long end = System.currentTimeMillis();
        long duration = end - start;
        double throughput = (double) EVENT_COUNT_HIGH / (duration / 1000.0);
        
        System.out.printf("Memory ReadReverse Throughput: %.2f ops/sec (%d events in %d ms)%n", throughput, EVENT_COUNT_HIGH, duration);
        
        Assert.assertEquals(readCount.get(), EVENT_COUNT_HIGH);
        Assert.assertTrue(throughput > 100_000, "Memory readReverse throughput too low: " + throughput);
    }

    @Test
    public void testDiskReadThroughput() throws Exception {
        // Write data first (buffered for speed)
        ledgerRegistry.setDefaultAutoFlush(false);
        ledger = createLedger();
        int count = EVENT_COUNT_LOW; // 100k for disk read
        for (int i = 0; i < count; i++) {
            ledger.write(new TestRecord(Instant.now(), "data", i));
        }
        ledger.close();

        // Open driver directly to test disk read speed (bypassing memory cache)
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();
        
        long start = System.currentTimeMillis();
        
        AtomicInteger readCount = new AtomicInteger(0);
        driver.read(-1, event -> {
            readCount.incrementAndGet();
            return true;
        });
        
        long end = System.currentTimeMillis();
        long duration = end - start;
        double throughput = (double) count / (duration / 1000.0);
        
        System.out.printf("Disk Read Throughput: %.2f ops/sec (%d events in %d ms)%n", throughput, count, duration);
        
        Assert.assertEquals(readCount.get(), count);
        Assert.assertTrue(throughput > 5_000, "Disk read throughput too low: " + throughput);
    }

    @Test
    public void testDiskReadReverseThroughput() throws Exception {
        // Write data first
        ledgerRegistry.setDefaultAutoFlush(false);
        ledger = createLedger();
        int count = EVENT_COUNT_LOW;
        for (int i = 0; i < count; i++) {
            ledger.write(new TestRecord(Instant.now(), "data", i));
        }
        ledger.close();

        // Open driver directly
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();
        
        long start = System.currentTimeMillis();
        
        AtomicInteger readCount = new AtomicInteger(0);
        driver.readReverse(-1, event -> {
            readCount.incrementAndGet();
            return true;
        });
        
        long end = System.currentTimeMillis();
        long duration = end - start;
        double throughput = (double) count / (duration / 1000.0);
        
        System.out.printf("Disk ReadReverse Throughput: %.2f ops/sec (%d events in %d ms)%n", throughput, count, duration);
        
        Assert.assertEquals(readCount.get(), count);
        // Reverse read on disk is slower due to seeking
        Assert.assertTrue(throughput > 1_000, "Disk readReverse throughput too low: " + throughput);
    }
}
