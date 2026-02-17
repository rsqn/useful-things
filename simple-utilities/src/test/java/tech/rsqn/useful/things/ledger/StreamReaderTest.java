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

public class StreamReaderTest {
    private Path tempDir;
    private MapLedgerConfig config;
    private ExecutorService executor;
    private LedgerRegistry registry;
    private StreamReader streamReader;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("stream-reader-test");
        config = new MapLedgerConfig();
        config.put("ledger.auto_flush", "true");
        executor = Executors.newCachedThreadPool();
        
        registry = new LedgerRegistry();
        registry.setConfig(config);
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);
        
        streamReader = new StreamReader(registry);
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
    public void testSubscribe() throws InterruptedException, IOException {
        CountDownLatch latch = new CountDownLatch(1);
        List<BaseEvent> received = new ArrayList<>();

        streamReader.subscribe(EventType.PRICE_UPDATE, event -> {
            received.add(event);
            latch.countDown();
        });

        Ledger ledger = registry.getLedger(EventType.PRICE_UPDATE);
        ledger.write(createData("val", 1), Instant.now());

        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
        Assert.assertEquals(received.size(), 1);
        Assert.assertEquals(((Number) received.get(0).getData().get("val")).intValue(), 1);
    }

    @Test
    public void testTailEventsHistory() throws IOException {
        Ledger ledger = registry.getLedger(EventType.PRICE_UPDATE);
        ledger.write(createData("val", 1), Instant.now());
        ledger.write(createData("val", 2), Instant.now());
        
        List<BaseEvent> events = new ArrayList<>();
        streamReader.tailEvents(EventType.PRICE_UPDATE, false, event -> {
            events.add(event);
            return true;
        });

        Assert.assertEquals(events.size(), 2);
        // From memory -> Integer
        Assert.assertEquals(((Number) events.get(0).getData().get("val")).intValue(), 1);
        Assert.assertEquals(((Number) events.get(1).getData().get("val")).intValue(), 2);
    }

    @Test
    public void testTailEventsFollow() throws IOException, InterruptedException {
        Ledger ledger = registry.getLedger(EventType.PRICE_UPDATE);
        ledger.write(createData("val", 1), Instant.now());

        // Start tailing in background thread because it blocks
        CountDownLatch latch = new CountDownLatch(2);
        List<BaseEvent> received = new ArrayList<>();
        
        Thread tailThread = new Thread(() -> {
            streamReader.tailEvents(EventType.PRICE_UPDATE, true, event -> {
                received.add(event);
                latch.countDown();
                return true;
            });
        });
        tailThread.start();

        // Wait for history read (should be fast)
        // But latch counts 2, so we need 1 history + 1 new
        
        // Write new event
        // Give time for tail thread to start and subscribe
        Thread.sleep(500); 
        ledger.write(createData("val", 2), Instant.now());

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(received.size(), 2);
        // First event from memory -> Integer
        Assert.assertEquals(((Number) received.get(0).getData().get("val")).intValue(), 1);
        // Second event from live -> Integer
        Assert.assertEquals(((Number) received.get(1).getData().get("val")).intValue(), 2);
        
        tailThread.interrupt(); // Stop infinite stream
    }

    private Map<String, Object> createData(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }
}
