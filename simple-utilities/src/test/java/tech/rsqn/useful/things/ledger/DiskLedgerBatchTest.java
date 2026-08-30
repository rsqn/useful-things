package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DiskLedgerBatchTest extends LedgerTestBase {

    @Test
    public void writeBatch_assignsSequences_notifiesInOrder_andScansBothDirections() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();

        DiskLedger<TestRecord> diskLedger = new DiskLedger<>(TestRecord.TYPE, driver);
        this.ledger = diskLedger;

        List<Long> notified = new ArrayList<>();
        CountDownLatch notificationLatch = new CountDownLatch(3);
        String previousSync = System.getProperty("tech.rsqn.useful.things.ledger.sync");
        System.setProperty("tech.rsqn.useful.things.ledger.sync", "true");
        try {
            diskLedger.subscribe(null, record -> {
                notified.add(record.getSequenceId());
                notificationLatch.countDown();
            });

            List<TestRecord> records = List.of(
                    createRecord("a", 1),
                    createRecord("b", 2),
                    createRecord("c", 3));
            Assert.assertEquals(diskLedger.assignSequenceId(records.get(0)), 1L);
            List<Long> sequenceIds = diskLedger.writeBatch(records);

            Assert.assertEquals(sequenceIds, List.of(1L, 2L, 3L));
            Assert.assertTrue(notificationLatch.await(10, TimeUnit.SECONDS));
            Assert.assertEquals(notified, List.of(1L, 2L, 3L));

            List<Integer> forward = new ArrayList<>();
            diskLedger.read(0, null, record -> {
                forward.add(record.getValue());
                return true;
            });
            Assert.assertEquals(forward, List.of(1, 2, 3));

            List<Integer> reverse = new ArrayList<>();
            diskLedger.readReverse(-1, null, record -> {
                reverse.add(record.getValue());
                return true;
            });
            Assert.assertEquals(reverse, List.of(3, 2, 1));
        } finally {
            if (previousSync == null) {
                System.clearProperty("tech.rsqn.useful.things.ledger.sync");
            } else {
                System.setProperty("tech.rsqn.useful.things.ledger.sync", previousSync);
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void writeBatch_rejectsUnreservedSequenceId() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();

        DiskLedger<TestRecord> diskLedger = new DiskLedger<>(TestRecord.TYPE, driver);
        this.ledger = diskLedger;
        TestRecord record = createRecord("unreserved", 99);
        record.setSequenceId(42L);

        diskLedger.writeBatch(List.of(record));
    }
}
