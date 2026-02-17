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
        
        registry = new LedgerRegistry();
        registry.setConfig(config);
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);
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
    public void testEndToEndFlow() throws Exception {
        Ledger ledger = registry.getLedger(EventType.PRICE_UPDATE);
        // No start() needed

        // 1. Subscribe
        CountDownLatch latch = new CountDownLatch(2);
        List<BaseEvent> received = new ArrayList<>();
        ledger.subscribe(event -> {
            received.add(event);
            latch.countDown();
        }, null);

        // 2. Write events
        Instant now = Instant.now();
        ledger.write(createData("val", 1), now);
        ledger.write(createData("val", 2), now.plusSeconds(1));

        // 3. Verify subscription received events
        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(received.size(), 2);
        Assert.assertEquals(((Number) received.get(0).getData().get("val")).intValue(), 1);
        Assert.assertEquals(((Number) received.get(1).getData().get("val")).intValue(), 2);

        // 4. Read back (from memory)
        List<BaseEvent> fromMemory = new ArrayList<>();
        ledger.read(-1, null, event -> {
            fromMemory.add(event);
            return true;
        });
        
        Assert.assertEquals(fromMemory.size(), 2);
        // From memory (direct write) -> Integer
        Assert.assertEquals(((Number) fromMemory.get(0).getData().get("val")).intValue(), 1);
        Assert.assertEquals(((Number) fromMemory.get(1).getData().get("val")).intValue(), 2);

        // 5. Close and reopen to force hydration from disk
        ledger.close();
        
        // Re-create registry/ledger
        registry = new LedgerRegistry();
        registry.setConfig(config);
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);
        
        ledger = registry.getLedger(EventType.PRICE_UPDATE);
        
        // 6. Read from memory (hydrated from disk)
        List<BaseEvent> hydrated = new ArrayList<>();
        ledger.read(-1, null, event -> {
            hydrated.add(event);
            return true;
        });
        
        Assert.assertEquals(hydrated.size(), 2);
        // From disk (Gson) -> Double
        Assert.assertEquals(((Number) hydrated.get(0).getData().get("val")).doubleValue(), 1.0);
        Assert.assertEquals(((Number) hydrated.get(1).getData().get("val")).doubleValue(), 2.0);

        // 7. Write new event
        ledger.write(createData("val", 3), now.plusSeconds(2));
        
        // 8. Read again - should have 3 events
        List<BaseEvent> all = new ArrayList<>();
        ledger.read(-1, null, event -> {
            all.add(event);
            return true;
        });
        
        Assert.assertEquals(all.size(), 3);
        // Third event was written directly to memory -> Integer
        Assert.assertEquals(((Number) all.get(2).getData().get("val")).intValue(), 3);

        ledger.close();
    }

    private Map<String, Object> createData(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }
}
