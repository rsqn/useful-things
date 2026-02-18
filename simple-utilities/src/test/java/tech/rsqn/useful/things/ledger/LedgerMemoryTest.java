package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

public class LedgerMemoryTest {
    private Path tempDir;
    private PersistenceDriver<TestRecord> driver;
    private LedgerRegistry ledgerRegistry;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("memory-test");
        ledgerRegistry = new LedgerRegistry();
        ledgerRegistry.setLedgerDir(tempDir);
        ledgerRegistry.registerRecordType(TestRecord.TYPE, TestRecord.class);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (driver != null) {
            driver.close();
        }
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    @Test(timeOut = 300000) // 5 minute timeout
    public void testStreamingMemoryStability() throws IOException {
        Path ledgerFile = tempDir.resolve("memory_test.jsonl");
        DiskPersistenceDriver<TestRecord> diskDriver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        diskDriver.setAutoFlush(false);
        diskDriver.setFlushIntervalWrites(1000);
        diskDriver.init();
        diskDriver.start();
        driver = diskDriver;
        
        // We need to write data first
        
        int eventCount = 10_000; // Enough to cause issues if leaking, but fast enough for test

        // 1. Generate data
        System.out.println("Generating " + eventCount + " events...");
        for (int i = 0; i < eventCount; i++) {
            TestRecord event = new TestRecord(Instant.now(), "val", i);
            event.setSequenceId((long) i);
            driver.write(event);
        }
        driver.flush();

        // 2. Read and monitor memory
        System.out.println("Reading events...");
        
        // Force GC before starting
        System.gc();
        long startMemory = getUsedMemory();
        System.out.println("Start Memory: " + formatBytes(startMemory));

        AtomicInteger count = new AtomicInteger(0);
        
        // Read in batches to simulate streaming processing
        // We read the WHOLE file multiple times to simulate "millions" of processed events
        // without needing gigabytes of disk space.
        int iterations = 5;
        
        for (int i = 0; i < iterations; i++) {
            driver.read(-1, e -> {
                count.incrementAndGet();
                // Simulate light processing
                if (e.getValue() < 0) {
                    throw new RuntimeException("Parse error");
                }
                return true;
            });
            
            // Suggest GC between iterations to clear short-lived objects
            // We want to find long-lived leaks
            if (i % 2 == 0) System.gc();
        }

        System.gc();
        long endMemory = getUsedMemory();
        System.out.println("End Memory: " + formatBytes(endMemory));
        System.out.println("Processed: " + count.get());

        Assert.assertEquals(count.get(), eventCount * iterations);

        // Allow for some fluctuation, but huge growth (e.g. > 20MB) would indicate a leak
        // given we processed 500k events.
        long diff = endMemory - startMemory;
        System.out.println("Memory Diff: " + formatBytes(diff));
        
        // 10MB tolerance for JVM overhead/fluctuation
        Assert.assertTrue(diff < 10 * 1024 * 1024, "Memory grew by " + formatBytes(diff) + ", possible leak");
    }

    private long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private String formatBytes(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
