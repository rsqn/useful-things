package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for {@link ConsumerMode} — verifying BLOCK mode delivers events correctly
 * with zero-spin semantics, and that SPIN mode remains unchanged.
 */
public class LedgerConsumerModeTest extends LedgerTestBase {

    private WriteBehindDiskLedger<TestRecord> createBlockModeLedger() throws IOException {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setAutoFlush(false);
        driver.setFlushIntervalWrites(1000);
        driver.init();
        driver.start();
        WriteBehindDiskLedger<TestRecord> wbl = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wbl.setWriteQueueCapacity(10_000);
        wbl.setConsumerMode(ConsumerMode.BLOCK);
        wbl.init();
        return wbl;
    }

    private WriteBehindDiskLedger<TestRecord> createSpinModeLedger() throws IOException {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setAutoFlush(false);
        driver.setFlushIntervalWrites(1000);
        driver.init();
        driver.start();
        WriteBehindDiskLedger<TestRecord> wbl = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wbl.setWriteQueueCapacity(10_000);
        wbl.setConsumerMode(ConsumerMode.SPIN);
        wbl.init();
        return wbl;
    }

    @Test
    public void blockMode_subscriberReceivesEvent() throws Exception {
        ledger = createBlockModeLedger();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger(0);

        ledger.subscribe(null, event -> {
            received.incrementAndGet();
            latch.countDown();
        });

        ledger.write(createRecord("hello", 42));

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS), "Subscriber should fire within 5s");
        Assert.assertEquals(received.get(), 1);
    }

    @Test
    public void blockMode_multipleEventsAllDelivered() throws Exception {
        ledger = createBlockModeLedger();
        int count = 100;
        CountDownLatch latch = new CountDownLatch(count);
        AtomicInteger received = new AtomicInteger(0);

        ledger.subscribe(null, event -> {
            received.incrementAndGet();
            latch.countDown();
        });

        for (int i = 0; i < count; i++) {
            ledger.write(createRecord("data", i));
        }

        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS), "All events should be delivered");
        Assert.assertEquals(received.get(), count);
    }

    @Test
    public void blockMode_multipleSubscribers_orderPreserved() throws Exception {
        ledger = createBlockModeLedger();
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);

        ledger.subscribe(null, r -> order.add(1));
        ledger.subscribe(null, r -> {
            order.add(2);
            done.countDown();
        });

        ledger.write(createRecord("ord", 0));

        Assert.assertTrue(done.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(order.get(0), Integer.valueOf(1));
        Assert.assertEquals(order.get(1), Integer.valueOf(2));
    }

    @Test
    public void blockMode_filterRespected() throws Exception {
        ledger = createBlockModeLedger();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger(0);

        ledger.subscribe(event -> event.getValue() > 10, event -> {
            received.incrementAndGet();
            latch.countDown();
        });

        // Should be filtered out
        ledger.write(createRecord("low", 5));
        // Should be accepted
        ledger.write(createRecord("high", 15));

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        // Give a moment to ensure the filtered one doesn't sneak through
        Thread.sleep(100);
        Assert.assertEquals(received.get(), 1);
    }

    @Test
    public void blockMode_closeUnblocksConsumers() throws Exception {
        WriteBehindDiskLedger<TestRecord> wbl = createBlockModeLedger();
        ledger = wbl;

        // Subscribe to force consumer thread start
        ledger.subscribe(null, event -> {});
        ledger.write(createRecord("trigger", 1));
        Thread.sleep(100); // Let consumer start and block on take()

        // close() should return within timeout (consumers unblocked by interrupt)
        long start = System.currentTimeMillis();
        ledger.close();
        long elapsed = System.currentTimeMillis() - start;
        ledger = null; // Prevent double-close in tearDown

        Assert.assertTrue(elapsed < 5000, "close() should complete quickly, took " + elapsed + "ms");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void setConsumerMode_afterConsumersStarted_throwsISE() throws Exception {
        WriteBehindDiskLedger<TestRecord> wbl = createBlockModeLedger();
        ledger = wbl;

        // Force consumers to start
        ledger.subscribe(null, event -> {});
        ledger.write(createRecord("start", 1));
        Thread.sleep(200); // Let consumers start

        // Should throw — consumers already running
        ((AbstractLedger<TestRecord>) ledger).setConsumerMode(ConsumerMode.SPIN);
    }

    @Test
    public void spinMode_unchanged_subscriberReceivesEvent() throws Exception {
        ledger = createSpinModeLedger();
        CountDownLatch latch = new CountDownLatch(1);

        ledger.subscribe(null, event -> latch.countDown());
        ledger.write(createRecord("spin", 1));

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void spinMode_unchanged_multipleEventsAllDelivered() throws Exception {
        ledger = createSpinModeLedger();
        int count = 100;
        CountDownLatch latch = new CountDownLatch(count);

        ledger.subscribe(null, event -> latch.countDown());

        for (int i = 0; i < count; i++) {
            ledger.write(createRecord("data", i));
        }

        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void blockMode_subscriberExceptionDoesNotKillConsumer() throws Exception {
        ledger = createBlockModeLedger();
        CountDownLatch secondLatch = new CountDownLatch(1);

        // First subscriber throws
        ledger.subscribe(null, event -> {
            throw new RuntimeException("boom");
        });
        // Second subscriber should still receive
        ledger.subscribe(null, event -> secondLatch.countDown());

        ledger.write(createRecord("err", 1));

        Assert.assertTrue(secondLatch.await(5, TimeUnit.SECONDS),
                "Second subscriber should fire despite first throwing");
    }

    @Test
    public void blockMode_highThroughput_noEventLost() throws Exception {
        ledger = createBlockModeLedger();
        int count = 10_000;
        CountDownLatch latch = new CountDownLatch(count);
        AtomicInteger received = new AtomicInteger(0);

        ledger.subscribe(null, event -> {
            received.incrementAndGet();
            latch.countDown();
        });

        for (int i = 0; i < count; i++) {
            ledger.write(createRecord("bulk", i));
        }

        Assert.assertTrue(latch.await(30, TimeUnit.SECONDS),
                "All 10k events should be delivered, got " + received.get());
        Assert.assertEquals(received.get(), count);
    }
}
