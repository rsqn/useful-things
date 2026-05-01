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
        Assert.assertNotNull(hc.get("writeQueueSize"));
        Assert.assertNotNull(hc.get("writeQueueRemainingCapacity"));
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
}
