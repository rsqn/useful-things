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
import java.util.stream.Stream;

public class LedgerMemoryTest {
    private Path tempDir;
    private MapLedgerConfig config;
    private EventLedger ledger;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("memory-test");
        config = new MapLedgerConfig();
        config.put("ledger.auto_flush", "false"); // Performance
        config.put("ledger.flush_interval_writes", "1000");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (ledger != null) {
            ledger.close();
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
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();
        // Ensure memory cache is DISABLED
        ledger.disableMemoryCache();

        int eventCount = 1_000_000; // Enough to cause issues if leaking, but fast enough for test
        Map<String, Object> data = new HashMap<>();
        data.put("price", 123.45);
        data.put("volume", 1000);
        data.put("symbol", "BTC-USD");
        // Add some noise to make JSON parsing work
        data.put("meta", "some metadata string to parse");

        // 1. Generate data
        System.out.println("Generating " + eventCount + " events...");
        ledger.bulkWriteMode(() -> {
            for (int i = 0; i < eventCount; i++) {
                ledger.writeEvent(data, Instant.now());
            }
        });
        ledger.forceFlush();

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
            try (Stream<BaseEvent> stream = ledger.readEvents(null)) {
                stream.forEach(e -> {
                    count.incrementAndGet();
                    // Simulate light processing
                    if (e.getData().get("price") == null) {
                        throw new RuntimeException("Parse error");
                    }
                });
            }
            
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

    @Test(timeOut = 300000)
    public void testJsonParsingMemoryIsolation() throws IOException {
        Path ledgerFile = tempDir.resolve("json_test.jsonl");
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        // No need to start() for parseEvent, but good practice
        ledger.start();

        // Test specifically the JSON parsing path in isolation
        // Note: event_type must match the value in EventType enum ("price_update" not "PRICE_UPDATE")
        String json = "{\"event_id\": 1, \"event_type\": \"price_update\", \"timestamp\": \"2023-01-01T00:00:00Z\", \"data\": {\"price\": 100.0, \"symbol\": \"BTC\"}}";
        
        // Warmup
        for (int i = 0; i < 1000; i++) {
            ledger.parseEvent(json);
        }
        
        System.gc();
        long startMemory = getUsedMemory();
        System.out.println("JSON Test Start Memory: " + formatBytes(startMemory));
        
        int iterations = 10_000_000;
        for (int i = 0; i < iterations; i++) {
            BaseEvent event = ledger.parseEvent(json);
            if (event == null) throw new RuntimeException("Failed to parse");
            // Consume object to prevent JIT elimination if possible, though side effects in parseEvent might be enough
            if (event.getEventId() != 1) throw new RuntimeException("Bad ID");
        }
        
        System.gc();
        long endMemory = getUsedMemory();
        System.out.println("JSON Test End Memory: " + formatBytes(endMemory));
        
        long diff = endMemory - startMemory;
        System.out.println("JSON Memory Diff: " + formatBytes(diff));
        
        // 5MB tolerance
        Assert.assertTrue(diff < 5 * 1024 * 1024, "JSON parsing caused memory growth: " + formatBytes(diff));
    }

    private long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private String formatBytes(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
