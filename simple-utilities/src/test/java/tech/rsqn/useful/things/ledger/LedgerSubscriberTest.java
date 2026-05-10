package tech.rsqn.useful.things.ledger;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class LedgerSubscriberTest extends LedgerTestBase {

    @Test
    public void testSubscribe() throws IOException {
        ledger = createLedger();

        Consumer<TestRecord> subscriber = Mockito.mock(Consumer.class);
        ledger.subscribe(null, subscriber);

        ledger.write(createRecord("val", 1));

        // Verify subscriber called (async)
        Mockito.verify(subscriber, Mockito.timeout(1000).times(1)).accept(Mockito.any(TestRecord.class));
    }

    @Test
    public void testSubscribeAsync() throws IOException, InterruptedException {
        ledger = createLedger();

        CountDownLatch latch = new CountDownLatch(1);
        ledger.subscribe(null, event -> latch.countDown());

        ledger.write(createRecord("val", 1));

        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testMultipleSubscribersEachInvokedOncePerRecord() throws IOException {
        ledger = createLedger();
        Consumer<TestRecord> sub1 = Mockito.mock(Consumer.class);
        Consumer<TestRecord> sub2 = Mockito.mock(Consumer.class);
        ledger.subscribe(null, sub1);
        ledger.subscribe(null, sub2);
        ledger.write(createRecord("x", 1));
        Mockito.verify(sub1, Mockito.timeout(1000).times(1)).accept(Mockito.any(TestRecord.class));
        Mockito.verify(sub2, Mockito.timeout(1000).times(1)).accept(Mockito.any(TestRecord.class));
    }

    @Test
    public void testSubscribersRunInSubscribeOrderForSameRecord() throws IOException, InterruptedException {
        ledger = createLedger();
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);
        ledger.subscribe(null, r -> order.add(1));
        ledger.subscribe(null, r -> {
            order.add(2);
            done.countDown();
        });
        ledger.write(createRecord("ord", 0));
        Assert.assertTrue(done.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(order, Arrays.asList(1, 2));
    }

    @Test
    public void testSubscriberExceptionDoesNotPreventLaterSubscribers() throws IOException {
        ledger = createLedger();
        Consumer<TestRecord> throwing = Mockito.mock(Consumer.class);
        Mockito.doThrow(new RuntimeException("first")).when(throwing).accept(Mockito.any());
        Consumer<TestRecord> second = Mockito.mock(Consumer.class);
        ledger.subscribe(null, throwing);
        ledger.subscribe(null, second);
        ledger.write(createRecord("e", 1));
        Mockito.verify(throwing, Mockito.timeout(1000).times(1)).accept(Mockito.any(TestRecord.class));
        Mockito.verify(second, Mockito.timeout(1000).times(1)).accept(Mockito.any(TestRecord.class));
    }

    @Test
    public void testFilteredSubscribe() throws IOException {
        ledger = createLedger();

        Consumer<TestRecord> subscriber = Mockito.mock(Consumer.class);
        
        // Subscribe with filter: only accept events where value > 10
        ledger.subscribe(event -> event.getValue() > 10, subscriber);

        // Write event that should be filtered OUT
        ledger.write(createRecord("val", 5));
        
        // Write event that should be ACCEPTED
        ledger.write(createRecord("val", 15));

        // Verify subscriber called only once (for the second event)
        Mockito.verify(subscriber, Mockito.timeout(1000).times(1)).accept(Mockito.argThat(event -> event.getValue() == 15));
    }

    @Test
    public void testConcurrentSubscribeWhileWriting() throws Exception {
        ledger = createLedger();
        int writes = 100;
        CountDownLatch writersDone = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 25; i++) {
                    ledger.subscribe(null, r -> { });
                }
            });
        }
        pool.submit(() -> {
            try {
                for (int i = 0; i < writes; i++) {
                    ledger.write(createRecord("c", i));
                }
            } finally {
                writersDone.countDown();
            }
        });
        Assert.assertTrue(writersDone.await(60, TimeUnit.SECONDS));
        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }

    @Test
    public void testNotificationBackpressureDeliversAllEvents() throws Exception {
        ledger = createLedger();
        AbstractLedger<TestRecord> al = (AbstractLedger<TestRecord>) ledger;
        al.setNotificationCorePoolSize(1);
        al.setNotificationMaxPoolSize(1);
        al.setNotificationQueueCapacity(1);

        int n = 12;
        CountDownLatch slowGate = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger(0);
        ledger.subscribe(null, r -> {
            try {
                if (!slowGate.await(15, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            delivered.incrementAndGet();
        });

        ExecutorService writer = Executors.newSingleThreadExecutor();
        Future<?> writeDone = writer.submit(() -> {
            for (int i = 0; i < n; i++) {
                ledger.write(createRecord("w", i));
            }
        });
        Assert.assertFalse(writeDone.isDone());
        slowGate.countDown();
        writeDone.get(45, TimeUnit.SECONDS);
        writer.shutdown();
        Assert.assertTrue(writer.awaitTermination(10, TimeUnit.SECONDS));

        long deadline = System.currentTimeMillis() + 30_000;
        while (delivered.get() < n && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        Assert.assertEquals(delivered.get(), n);
    }
}
