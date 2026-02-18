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
    private ExecutorService executor;
    private LedgerRegistry registry;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("housekeeping-test");
        executor = Executors.newCachedThreadPool();
        
        registry = new LedgerRegistry();
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);
        registry.setDefaultAutoFlush(false); // Disable auto flush to test manual flush
        
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
    public void testFlushAllLedgers() throws IOException, InterruptedException {
        Ledger<TestRecord> ledger = registry.getLedger(TestRecord.TYPE);
        // No start() needed
        
        ledger.write(new TestRecord(Instant.now(), "val", 1));

        // Wait a bit for async write to hit the driver (but driver buffers)
        Thread.sleep(500);

        Path ledgerPath = tempDir.resolve(TestRecord.TYPE.getValue() + ".jsonl");

        // Should not be flushed yet (file might exist but be empty or have partial buffer not written to disk if OS buffers? 
        // No, BufferedWriter buffers in heap. If not flushed, file size on disk is 0 or unchanged.)
        // But WriteBehindMemoryLedger uses a background thread.
        // If auto_flush is false, DiskPersistenceDriver buffers.
        
        if (Files.exists(ledgerPath)) {
            Assert.assertEquals(Files.size(ledgerPath), 0);
        }

        int count = LedgerHousekeeping.flushAllLedgers(registry);
        Assert.assertTrue(count > 0);
        
        Thread.sleep(100); // Allow flush to complete

        // Should be flushed
        Assert.assertTrue(Files.size(ledgerPath) > 0);
    }

    @Test
    public void testHousekeepingThread() throws InterruptedException, IOException {
        Thread thread = LedgerHousekeeping.startHousekeepingThread(registry, 1);
        Assert.assertTrue(thread.isAlive());
        Assert.assertTrue(thread.isDaemon());
        
        thread.interrupt();
        thread.join(1000);
        Assert.assertFalse(thread.isAlive());
    }
}
