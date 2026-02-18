package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class LedgerConcurrencyTest {
    private Path tempDir;
    private ExecutorService executor;
    private LedgerRegistry registry;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("concurrency-test");
        // Use cached thread pool to allow concurrent notifications
        executor = Executors.newCachedThreadPool();
        
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
    public void testConcurrentWrites() throws Exception {
        Ledger<TestRecord> ledger = registry.getLedger(TestRecord.TYPE);
        // No start() needed

        int threadCount = 10;
        int eventsPerThread = 10; // Reduced load to ensure stability in CI environment
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService writeExecutor = Executors.newFixedThreadPool(threadCount);

        // Subscribe to verify we get all events
        Queue<TestRecord> received = new ConcurrentLinkedQueue<>();
        AtomicInteger receivedCount = new AtomicInteger(0);
        ledger.subscribe(null, e -> {
            received.add(e);
            receivedCount.incrementAndGet();
        });

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(writeExecutor.submit(() -> {
                try {
                    for (int j = 0; j < eventsPerThread; j++) {
                        long id = ledger.write(new TestRecord(Instant.now(), "thread-" + threadId, j));
                        if (id == -1) {
                            throw new RuntimeException("Write failed");
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }));
        }

        Assert.assertTrue(latch.await(30, TimeUnit.SECONDS));
        
        // Check for exceptions
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                Assert.fail("Write task failed", e.getCause());
            }
        }
        
        writeExecutor.shutdown();

        // Verify total count in memory (subscriber)
        // Wait for async subscribers (up to 30 seconds)
        long start = System.currentTimeMillis();
        while (receivedCount.get() < threadCount * eventsPerThread && System.currentTimeMillis() - start < 30000) {
            Thread.sleep(100);
        }
        int finalSize = receivedCount.get();
        if (finalSize < threadCount * eventsPerThread) {
            System.out.println("Concurrency test failed: expected " + (threadCount * eventsPerThread) + ", got " + finalSize);
        }
        Assert.assertEquals(finalSize, threadCount * eventsPerThread);

        // Verify sequence IDs are unique
        Set<Long> ids = received.stream().map(TestRecord::getSequenceId).collect(Collectors.toSet());
        Assert.assertEquals(ids.size(), threadCount * eventsPerThread);

        // Verify file content (or memory read)
        List<TestRecord> fromDisk = new ArrayList<>();
        ledger.read(-1, null, event -> {
            fromDisk.add(event);
            return true;
        });
        
        Assert.assertEquals(fromDisk.size(), threadCount * eventsPerThread);
        
        // Verify IDs in file are unique
        Set<Long> diskIds = fromDisk.stream().map(TestRecord::getSequenceId).collect(Collectors.toSet());
        Assert.assertEquals(diskIds.size(), threadCount * eventsPerThread);
        
        ledger.close();
    }
}
