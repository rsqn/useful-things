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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LedgerIntegrationTest {
    private Path tempDir;
    private ExecutorService executor;
    private LedgerRegistry registry;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("integration-test");
        // Use single thread executor to ensure ordered delivery of notifications in tests
        executor = Executors.newSingleThreadExecutor();
        
        registry = new LedgerRegistry();
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);
        registry.setDefaultAutoFlush(true);
        
        registry.registerRecordType(TestRecord.TYPE, TestRecord.class);
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
        Ledger<TestRecord> ledger = registry.getLedger(TestRecord.TYPE);
        // No start() needed

        // 1. Subscribe
        CountDownLatch latch = new CountDownLatch(2);
        List<TestRecord> received = new ArrayList<>();
        ledger.subscribe(event -> {
            received.add(event);
            latch.countDown();
        }, null);

        // 2. Write events
        Instant now = Instant.now();
        ledger.write(new TestRecord(now, "val", 1));
        ledger.write(new TestRecord(now.plusSeconds(1), "val", 2));

        // 3. Verify subscription received events
        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(received.size(), 2);
        Assert.assertEquals(received.get(0).getValue(), 1);
        Assert.assertEquals(received.get(1).getValue(), 2);

        // 4. Read back (from memory)
        List<TestRecord> fromMemory = new ArrayList<>();
        ledger.read(-1, null, event -> {
            fromMemory.add(event);
            return true;
        });
        
        Assert.assertEquals(fromMemory.size(), 2);
        Assert.assertEquals(fromMemory.get(0).getValue(), 1);
        Assert.assertEquals(fromMemory.get(1).getValue(), 2);

        // 5. Close and reopen to force hydration from disk
        ledger.close();
        
        // Re-create registry/ledger
        registry = new LedgerRegistry();
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);
        registry.setDefaultAutoFlush(true);
        registry.registerRecordType(TestRecord.TYPE, TestRecord.class);
        
        ledger = registry.getLedger(TestRecord.TYPE);
        
        // 6. Read from memory (hydrated from disk)
        List<TestRecord> hydrated = new ArrayList<>();
        ledger.read(-1, null, event -> {
            hydrated.add(event);
            return true;
        });
        
        Assert.assertEquals(hydrated.size(), 2);
        // From disk (Gson) -> int
        Assert.assertEquals(hydrated.get(0).getValue(), 1);
        Assert.assertEquals(hydrated.get(1).getValue(), 2);

        // 7. Write new event
        ledger.write(new TestRecord(now.plusSeconds(2), "val", 3));
        
        // 8. Read again - should have 3 events
        List<TestRecord> all = new ArrayList<>();
        ledger.read(-1, null, event -> {
            all.add(event);
            return true;
        });
        
        Assert.assertEquals(all.size(), 3);
        Assert.assertEquals(all.get(2).getValue(), 3);

        ledger.close();
    }
}
