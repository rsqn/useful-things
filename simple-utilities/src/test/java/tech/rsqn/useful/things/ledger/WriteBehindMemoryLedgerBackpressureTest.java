package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class WriteBehindMemoryLedgerBackpressureTest {

    private static final class BlockingDriver implements PersistenceDriver<TestRecord> {
        private final CountDownLatch allowWrites = new CountDownLatch(1);
        private final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        private final AtomicInteger flushCalls = new AtomicInteger(0);
        private final List<Long> persistedSeq = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void write(TestRecord record) throws IOException {
            firstWriteStarted.countDown();
            try {
                allowWrites.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted waiting for allowWrites", e);
            }
            persistedSeq.add(record.getSequenceId());
        }

        @Override
        public void read(long fromSequence, ReadCallback<TestRecord> callback) {
            // not needed for these tests
        }

        @Override
        public void readReverse(long fromSequence, ReadCallback<TestRecord> callback) {
            // not needed for these tests
        }

        @Override
        public void flush() throws IOException {
            flushCalls.incrementAndGet();
        }

        @Override
        public void close() {
        }

        void releaseWrites() {
            allowWrites.countDown();
        }
    }

    private static TestRecord rec(int v) {
        return new TestRecord(Instant.now(), "v", v);
    }

    @Test
    public void writeBackpressureBlocksWhenQueueFullAndWriterStalled() throws Exception {
        BlockingDriver driver = new BlockingDriver();
        WriteBehindMemoryLedger<TestRecord> ledger = new WriteBehindMemoryLedger<>(TestRecord.TYPE, driver, null);
        ledger.setWriteQueueCapacity(1);
        ledger.init();

        // First write: writer thread will take it and block inside driver.write().
        ledger.write(rec(1));
        Assert.assertTrue(driver.firstWriteStarted.await(5, TimeUnit.SECONDS), "writer should start first write");

        // Second write fills the bounded queue (capacity 1).
        ledger.write(rec(2));

        // Third write should block due to backpressure (never drop).
        var exec = Executors.newSingleThreadExecutor();
        try {
            Future<Long> f = exec.submit(() -> ledger.write(rec(3)));

            try {
                f.get(150, TimeUnit.MILLISECONDS);
                Assert.fail("Expected third write to block while writer is stalled and queue is full");
            } catch (TimeoutException expected) {
                // good: blocked
            }

            driver.releaseWrites();
            long seq3 = f.get(5, TimeUnit.SECONDS);
            Assert.assertTrue(seq3 > 0, "write should complete after writer unblocks");
        } finally {
            exec.shutdownNow();
            ledger.close();
        }
    }

    @Test
    public void flushWaitsUntilLastEnqueuedIsPersisted() throws Exception {
        BlockingDriver driver = new BlockingDriver();
        WriteBehindMemoryLedger<TestRecord> ledger = new WriteBehindMemoryLedger<>(TestRecord.TYPE, driver, null);
        ledger.setWriteQueueCapacity(10);
        ledger.init();

        long s1 = ledger.write(rec(1));
        long s2 = ledger.write(rec(2));
        Assert.assertTrue(s2 > s1);

        var exec = Executors.newSingleThreadExecutor();
        try {
            Future<?> flushing = exec.submit(() -> {
                ledger.flush();
                return null;
            });

            // flush should block until we release driver writes (writer can't persist yet)
            try {
                flushing.get(150, TimeUnit.MILLISECONDS);
                Assert.fail("Expected flush() to block until persistence catches up");
            } catch (TimeoutException expected) {
                // good
            }

            driver.releaseWrites();
            flushing.get(5, TimeUnit.SECONDS);

            Assert.assertTrue(driver.persistedSeq.contains(s1), "first seq must be persisted before flush returns");
            Assert.assertTrue(driver.persistedSeq.contains(s2), "second seq must be persisted before flush returns");
            Assert.assertTrue(driver.flushCalls.get() >= 1, "flush should call driver.flush()");
        } finally {
            exec.shutdownNow();
            ledger.close();
        }
    }
}

