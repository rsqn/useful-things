package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LedgerIntegrationTest {
    private Path tempDir;
    private MapLedgerConfig config;
    private ExecutorService executor;
    private LedgerRegistry registry;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("integration-test");
        config = new MapLedgerConfig();
        config.put("ledger.auto_flush", "true");
        executor = Executors.newCachedThreadPool();
        registry = new LedgerRegistry(config, tempDir, executor);
    }

    @AfterMethod
    public void tearDown() throws IOException {
        if (executor != null) {
            executor.shutdownNow();
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

    @Test
    public void testEndToEndFlow() throws IOException, InterruptedException {
        EventLedger ledger = registry.getLedger(EventType.PRICE_UPDATE);
        ledger.start();

        // 1. Subscribe
        CountDownLatch latch = new CountDownLatch(2);
        List<BaseEvent> received = new ArrayList<>();
        ledger.subscribe(event -> {
            received.add(event);
            latch.countDown();
        });

        // 2. Write events
        Instant now = Instant.now();
        ledger.writeEvent(createData("val", 1), now);
        ledger.writeEvent(createData("val", 2), now.plusSeconds(1));

        // 3. Verify subscription received events
        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(received.size(), 2);
        Assert.assertEquals(((Number) received.get(0).getData().get("val")).intValue(), 1);
        Assert.assertEquals(((Number) received.get(1).getData().get("val")).intValue(), 2);

        // 4. Read back from disk (memory cache disabled by default)
        try (Stream<BaseEvent> stream = ledger.readEvents(null)) {
            List<BaseEvent> fromDisk = stream.collect(Collectors.toList());
            Assert.assertEquals(fromDisk.size(), 2);
            // From disk -> Double
            Assert.assertEquals(((Number) fromDisk.get(0).getData().get("val")).doubleValue(), 1.0);
            Assert.assertEquals(((Number) fromDisk.get(1).getData().get("val")).doubleValue(), 2.0);
        }

        // 5. Hydrate into memory
        ledger.enableMemoryCache(); // This clears cache, but we want to populate it
        // Actually enableMemoryCache just sets flag. hydrate clears and populates.
        // But if we just enabled, cache is empty.
        // Let's use hydrate.
        int count = ledger.hydrate(null);
        Assert.assertEquals(count, 2);

        // 6. Read from memory
        try (Stream<BaseEvent> stream = ledger.readEvents(null)) {
            List<BaseEvent> fromMemory = stream.collect(Collectors.toList());
            Assert.assertEquals(fromMemory.size(), 2);
            // From memory (hydrated from disk) -> Double (because hydrate parses from file)
            // Wait, hydrate calls parseEvent which uses Gson to Map.
            // So it will be Double.
            Assert.assertEquals(((Number) fromMemory.get(0).getData().get("val")).doubleValue(), 1.0);
        }

        // 7. Write new event with memory enabled
        ledger.writeEvent(createData("val", 3), now.plusSeconds(2));
        
        // 8. Read again - should have 3 events
        try (Stream<BaseEvent> stream = ledger.readEvents(null)) {
            List<BaseEvent> all = stream.collect(Collectors.toList());
            Assert.assertEquals(all.size(), 3);
            // Third event was written directly to memory -> Integer (original object)
            Assert.assertEquals(((Number) all.get(2).getData().get("val")).intValue(), 3);
        }

        ledger.stop();
    }

    private Map<String, Object> createData(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }
}
