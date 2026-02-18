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

public class StreamReaderTest {
    private Path tempDir;
    private ExecutorService executor;
    private LedgerRegistry registry;
    private StreamReader streamReader;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("stream-reader-test");
        executor = Executors.newCachedThreadPool();
        
        registry = new LedgerRegistry();
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);
        registry.setDefaultAutoFlush(true);
        
        streamReader = new StreamReader(registry);
        
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
    public void testSubscribe() throws InterruptedException, IOException {
        CountDownLatch latch = new CountDownLatch(1);
        List<TestRecord> received = new ArrayList<>();

        streamReader.subscribe(TestRecord.TYPE, event -> {
            if (event instanceof TestRecord) {
                received.add((TestRecord) event);
                latch.countDown();
            }
        });

        Ledger<TestRecord> ledger = registry.getLedger(TestRecord.TYPE);
        ledger.write(new TestRecord(Instant.now(), "val", 1));

        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
        Assert.assertEquals(received.size(), 1);
        Assert.assertEquals(received.get(0).getValue(), 1);
    }

    @Test
    public void testTailEventsHistory() throws IOException {
        Ledger<TestRecord> ledger = registry.getLedger(TestRecord.TYPE);
        Instant now = Instant.now();
        ledger.write(new TestRecord(now, "val", 1));
        ledger.write(new TestRecord(now, "val", 2));
        
        List<TestRecord> events = new ArrayList<>();
        streamReader.tailEvents(TestRecord.TYPE, false, event -> {
            if (event instanceof TestRecord) {
                events.add((TestRecord) event);
            }
            return true;
        });

        Assert.assertEquals(events.size(), 2);
        Assert.assertEquals(events.get(0).getValue(), 1);
        Assert.assertEquals(events.get(1).getValue(), 2);
    }

    @Test
    public void testTailEventsFollow() throws IOException, InterruptedException {
        Ledger<TestRecord> ledger = registry.getLedger(TestRecord.TYPE);
        Instant now = Instant.now();
        ledger.write(new TestRecord(now, "val", 1));

        // Start tailing in background thread because it blocks
        CountDownLatch latch = new CountDownLatch(2);
        List<TestRecord> received = new ArrayList<>();
        
        Thread tailThread = new Thread(() -> {
            streamReader.tailEvents(TestRecord.TYPE, true, event -> {
                if (event instanceof TestRecord) {
                    received.add((TestRecord) event);
                    latch.countDown();
                }
                return true;
            });
        });
        tailThread.start();

        // Wait for history read (should be fast)
        // But latch counts 2, so we need 1 history + 1 new
        
        // Write new event
        // Give time for tail thread to start and subscribe
        Thread.sleep(500); 
        ledger.write(new TestRecord(now, "val", 2));

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(received.size(), 2);
        Assert.assertEquals(received.get(0).getValue(), 1);
        Assert.assertEquals(received.get(1).getValue(), 2);
        
        tailThread.interrupt(); // Stop infinite stream
    }
}
