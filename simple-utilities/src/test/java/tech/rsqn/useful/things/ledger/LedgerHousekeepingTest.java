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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LedgerHousekeepingTest {
    private Path tempDir;
    private MapLedgerConfig config;
    private ExecutorService executor;
    private LedgerRegistry registry;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("housekeeping-test");
        config = new MapLedgerConfig();
        config.put("ledger.auto_flush", "false"); // Disable auto flush to test manual flush
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
    public void testFlushAllLedgers() throws IOException {
        EventLedger ledger = registry.getLedger(EventType.PRICE_UPDATE);
        ledger.start();
        ledger.writeEvent(createData("val", 1), Instant.now());

        // Should not be flushed yet
        Assert.assertEquals(Files.size(ledger.getLedgerPath()), 0);

        int count = LedgerHousekeeping.flushAllLedgers(registry);
        Assert.assertTrue(count > 0);

        // Should be flushed
        Assert.assertTrue(Files.size(ledger.getLedgerPath()) > 0);
    }

    @Test
    public void testHousekeepingThread() throws InterruptedException, IOException {
        // Start thread with short interval (though flush is hardcoded to 60s in implementation)
        // We can't easily test the loop timing without refactoring for clock injection
        // But we can test that it starts and can be interrupted
        
        Thread thread = LedgerHousekeeping.startHousekeepingThread(registry, 1);
        Assert.assertTrue(thread.isAlive());
        Assert.assertTrue(thread.isDaemon());
        
        thread.interrupt();
        thread.join(1000);
        Assert.assertFalse(thread.isAlive());
    }

    private java.util.Map<String, Object> createData(String key, Object value) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put(key, value);
        return data;
    }
}
