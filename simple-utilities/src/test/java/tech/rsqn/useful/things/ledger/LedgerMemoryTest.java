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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LedgerMemoryTest {
    private Path tempDir;
    private MapLedgerConfig config;
    private PersistenceDriver driver;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("memory-test");
        config = new MapLedgerConfig();
        config.put("ledger.auto_flush", "false"); // Performance
        config.put("ledger.flush_interval_writes", "1000");
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
        driver = new DiskPersistenceDriver(ledgerFile, config);
        // We need to write data first
        
        int eventCount = 10_000; // Enough to cause issues if leaking, but fast enough for test
        Map<String, Object> data = new HashMap<>();
        data.put("price", 123.45);
        data.put("volume", 1000);
        data.put("symbol", "BTC-USD");
        // Add some noise to make JSON parsing work
        data.put("meta", "some metadata string to parse");

        // 1. Generate data
        System.out.println("Generating " + eventCount + " events...");
        for (int i = 0; i < eventCount; i++) {
            BaseEvent event = new BaseEvent(EventType.PRICE_UPDATE, Instant.now(), data, (long) i);
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
                if (e.getData().get("price") == null) {
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
