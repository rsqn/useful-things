package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class WriteBehindMemoryLedgerScanTest extends LedgerTestBase {

    @Test
    public void singleWritesCoalesced_forwardAndReverseScanPreserveOrder() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        WriteBehindMemoryLedger<TestRecord> wb = openWriteBehind();
        for (int i = 1; i <= 5; i++) {
            wb.write(createRecord("r" + i, i));
        }
        wb.flush();

        assertForwardOrder(wb, List.of(1, 2, 3, 4, 5));
        assertReverseOrder(wb, List.of(5, 4, 3, 2, 1));
    }

    @Test
    public void writeBatch_forwardAndReverseScanPreserveOrder() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        WriteBehindMemoryLedger<TestRecord> wb = openWriteBehind();
        wb.writeBatch(List.of(
                createRecord("a", 10),
                createRecord("b", 20),
                createRecord("c", 30)));
        wb.flush();

        assertForwardOrder(wb, List.of(10, 20, 30));
        assertReverseOrder(wb, List.of(30, 20, 10));
    }

    @Test
    public void reopenHydrated_forwardAndReverseScanPreserveOrder() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        WriteBehindMemoryLedger<TestRecord> wb = openWriteBehind();
        for (int i = 1; i <= 4; i++) {
            wb.write(createRecord("v" + i, i));
        }
        wb.flush();
        wb.close();
        ledger = null;

        WriteBehindMemoryLedger<TestRecord> reopened = openWriteBehind();
        ledger = reopened;

        assertForwardOrder(reopened, List.of(1, 2, 3, 4));
        assertReverseOrder(reopened, List.of(4, 3, 2, 1));
    }

    @Test
    public void readReverseFallsBackToDiskAfterHousekeepingWithCoalescedWrites() throws Exception {
        ledgerRegistry.setDefaultPreferredMaxSize(1);
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        WriteBehindMemoryLedger<TestRecord> wb = openWriteBehind();
        wb.write(createRecord("one", 1));
        wb.write(createRecord("two", 2));
        wb.write(createRecord("three", 3));
        wb.flush();
        wb.housekeeping();

        List<Integer> reverse = new ArrayList<>();
        wb.readReverse(-1, null, record -> {
            reverse.add(record.getValue());
            return true;
        });

        Assert.assertEquals(reverse, List.of(3, 2, 1));
    }

    private WriteBehindMemoryLedger<TestRecord> openWriteBehind() throws IOException {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setAutoFlush(ledgerRegistry.isDefaultAutoFlush());
        driver.setFlushIntervalWrites(ledgerRegistry.getDefaultFlushIntervalWrites());
        driver.setFlushIntervalSeconds(ledgerRegistry.getDefaultFlushIntervalSeconds());
        driver.init();
        driver.start();

        WriteBehindMemoryLedger<TestRecord> wb =
                new WriteBehindMemoryLedger<>(TestRecord.TYPE, driver, null);
        wb.setPreferredMaxSize(ledgerRegistry.getDefaultPreferredMaxSize());
        wb.setWriteQueueCapacity(10_000);
        wb.init();
        this.ledger = wb;
        return wb;
    }

    private static void assertForwardOrder(Ledger<TestRecord> ledger, List<Integer> expected) {
        List<Integer> forward = new ArrayList<>();
        ledger.read(-1, null, record -> {
            forward.add(record.getValue());
            return true;
        });
        Assert.assertEquals(forward, expected);
    }

    private static void assertReverseOrder(Ledger<TestRecord> ledger, List<Integer> expected) {
        List<Integer> reverse = new ArrayList<>();
        ledger.readReverse(-1, null, record -> {
            reverse.add(record.getValue());
            return true;
        });
        Assert.assertEquals(reverse, expected);
    }
}
