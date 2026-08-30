package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.util.List;

public class MemoryLedgerBatchTest extends LedgerTestBase {

    @Test
    public void writeBatch_persistsAndRetainsAllRecords() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();

        MemoryLedger<TestRecord> memoryLedger = new MemoryLedger<>(TestRecord.TYPE, driver, null);
        memoryLedger.init();
        this.ledger = memoryLedger;

        List<Long> sequenceIds = memoryLedger.writeBatch(List.of(
                createRecord("a", 1),
                createRecord("b", 2),
                createRecord("c", 3)));

        Assert.assertEquals(sequenceIds, List.of(1L, 2L, 3L));
        Assert.assertEquals(memoryLedger.size(), 3L);
        memoryLedger.flush();
        Assert.assertEquals(Files.readAllLines(ledgerFile).size(), 3);
    }

    @Test
    public void writeBehindBatch_persistsAllRecords() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();

        WriteBehindMemoryLedger<TestRecord> memoryLedger =
                new WriteBehindMemoryLedger<>(TestRecord.TYPE, driver, null);
        memoryLedger.init();
        this.ledger = memoryLedger;

        Assert.assertEquals(memoryLedger.writeBatch(List.of(
                createRecord("a", 1),
                createRecord("b", 2),
                createRecord("c", 3))), List.of(1L, 2L, 3L));
        memoryLedger.flush();

        Assert.assertEquals(Files.readAllLines(ledgerFile).size(), 3);
    }
}
