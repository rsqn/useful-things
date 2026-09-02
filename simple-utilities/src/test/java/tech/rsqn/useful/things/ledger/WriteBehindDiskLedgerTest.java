package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WriteBehindDiskLedgerTest extends LedgerTestBase {

    @Test(expectedExceptions = IllegalStateException.class)
    public void writeBeforeInit_throws() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        try {
            wb.write(createRecord("x", 1));
        } finally {
            driver.close();
        }
    }

    @Test
    public void flushPersistsAllQueuedRecords() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(50);
        wb.init();
        this.ledger = wb;

        wb.write(createRecord("a", 1));
        wb.write(createRecord("b", 2));

        Assert.assertEquals(Files.size(ledgerFile), 0L, "disk append should still be on writer thread");

        wb.flush();

        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 2);
    }

    @Test
    public void writeBatch_flushPersistsAllRecordsInOrder() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(50);
        wb.init();
        this.ledger = wb;

        Assert.assertEquals(wb.writeBatch(List.of(
                createRecord("a", 1),
                createRecord("b", 2),
                createRecord("c", 3))), List.of(1L, 2L, 3L));
        wb.flush();

        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 3);
        Assert.assertTrue(lines.get(0).contains("\"data\":\"a\""));
        Assert.assertTrue(lines.get(1).contains("\"data\":\"b\""));
        Assert.assertTrue(lines.get(2).contains("\"data\":\"c\""));
    }

    @Test
    public void closeDrainsQueueToDisk() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(true);

        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(10);
        wb.init();
        this.ledger = wb;

        for (int i = 0; i < 100; i++) {
            wb.write(createRecord("r" + i, i));
        }

        wb.close();
        this.ledger = null;

        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 100);
    }

    @Test
    public void subscriberNotifiedBeforeFlush() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(20);
        wb.init();
        this.ledger = wb;

        AtomicInteger notifications = new AtomicInteger();
        List<Long> seenSeq = new ArrayList<>();
        CountDownLatch notified = new CountDownLatch(1);
        wb.subscribe(r -> true, r -> {
            notifications.incrementAndGet();
            seenSeq.add(r.getSequenceId());
            notified.countDown();
        });

        wb.write(createRecord("n", 1));

        Assert.assertTrue(notified.await(10, TimeUnit.SECONDS), "subscriber should run");
        Assert.assertEquals(notifications.get(), 1);

        wb.flush();
        Assert.assertEquals(Files.readAllLines(ledgerFile).size(), 1);
        Assert.assertEquals(seenSeq.get(0).longValue(), 1L);
    }

    @Test
    public void healthCheckExposesQueueMetrics() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(500);
        wb.init();
        this.ledger = wb;

        var hc = wb.healthCheck();
        Assert.assertEquals(hc.get("writeQueueCapacity"), 500);
        Assert.assertEquals(hc.get("persistBatchSize"), LedgerPersistBatch.DEFAULT_BATCH_SIZE);
        Assert.assertNotNull(hc.get("writeQueueSize"));
        Assert.assertNotNull(hc.get("writeQueueRemainingCapacity"));
    }

    @Test
    public void singleWritesCoalesceIntoBatchAppends() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        RecordingDriver driver = new RecordingDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(50);
        wb.init();
        this.ledger = wb;

        wb.write(createRecord("a", 1));
        wb.write(createRecord("b", 2));
        wb.write(createRecord("c", 3));
        wb.flush();

        Assert.assertEquals(driver.batchAppendCount.get(), 1);
        Assert.assertEquals(driver.batchSizes.get(0).intValue(), 3);
    }

    @Test
    public void singleWritesCoalesced_reverseScanReadsNewestFirst() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(50);
        wb.init();
        this.ledger = wb;

        wb.write(createRecord("a", 1));
        wb.write(createRecord("b", 2));
        wb.write(createRecord("c", 3));
        wb.flush();

        List<Integer> values = new ArrayList<>();
        wb.readReverse(-1, null, r -> {
            values.add(r.getValue());
            return true;
        });

        Assert.assertEquals(values, List.of(3, 2, 1));
    }

    @Test
    public void persistFailureSurfacesOnFlushAndBlocksFurtherWrites() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        FailingDriver driver = new FailingDriver(createDriver());
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(50);
        wb.init();
        this.ledger = wb;

        wb.write(createRecord("x", 1));

        try {
            wb.flush();
            Assert.fail("expected flush to surface persistence failure");
        } catch (LedgerPersistException expected) {
            Assert.assertNotNull(expected.getCause());
        }

        try {
            wb.write(createRecord("y", 2));
            Assert.fail("expected subsequent write to be rejected after terminal failure");
        } catch (LedgerPersistException expected) {
            // good
        }
    }

    @Test
    public void readReverseReadsNewestFirst() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(50);
        wb.init();
        this.ledger = wb;

        wb.write(createRecord("a", 1));
        wb.write(createRecord("b", 2));
        wb.write(createRecord("c", 3));
        wb.flush();

        List<Integer> values = new ArrayList<>();
        wb.readReverse(-1, null, r -> {
            values.add(r.getValue());
            return true;
        });

        Assert.assertEquals(values.size(), 3);
        Assert.assertEquals(values.get(0).intValue(), 3, "reverse read must return newest first");
        Assert.assertEquals(values.get(1).intValue(), 2);
        Assert.assertEquals(values.get(2).intValue(), 1);
    }

    private DiskPersistenceDriver<TestRecord> createDriver() {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setAutoFlush(ledgerRegistry.isDefaultAutoFlush());
        driver.setFlushIntervalWrites(ledgerRegistry.getDefaultFlushIntervalWrites());
        driver.setFlushIntervalSeconds(ledgerRegistry.getDefaultFlushIntervalSeconds());
        driver.init();
        try {
            driver.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return driver;
    }

    private static final class RecordingDriver implements PersistenceDriver<TestRecord> {
        private final AtomicInteger batchAppendCount = new AtomicInteger();
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public void write(TestRecord record) {
            throw new UnsupportedOperationException("single write should not be used");
        }

        @Override
        public void writeBatch(List<TestRecord> records) {
            batchAppendCount.incrementAndGet();
            batchSizes.add(records.size());
        }

        @Override
        public void read(long fromSequence, ReadCallback<TestRecord> callback) {
        }

        @Override
        public void readReverse(long fromSequence, ReadCallback<TestRecord> callback) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class FailingDriver implements PersistenceDriver<TestRecord> {
        private final DiskPersistenceDriver<TestRecord> delegate;

        private FailingDriver(DiskPersistenceDriver<TestRecord> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(TestRecord record) throws IOException {
            delegate.write(record);
        }

        @Override
        public void writeBatch(List<TestRecord> records) throws IOException {
            throw new IOException("simulated disk failure");
        }

        @Override
        public void read(long fromSequence, ReadCallback<TestRecord> callback) {
            delegate.read(fromSequence, callback);
        }

        @Override
        public void readReverse(long fromSequence, ReadCallback<TestRecord> callback) {
            delegate.readReverse(fromSequence, callback);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws Exception {
            delegate.close();
        }
    }
}
