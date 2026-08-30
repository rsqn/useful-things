package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.util.List;

public class DiskPersistenceDriverBatchTest extends LedgerTestBase {

    @Test
    public void writeBatch_appendsAllJsonlRowsInOrder() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();

        List<TestRecord> records = List.of(
                createRecord("a", 1),
                createRecord("b", 2),
                createRecord("c", 3));
        driver.writeBatch(records);
        driver.flush();
        driver.close();

        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 3);
        Assert.assertTrue(lines.get(0).contains("\"data\":\"a\""));
        Assert.assertTrue(lines.get(1).contains("\"data\":\"b\""));
        Assert.assertTrue(lines.get(2).contains("\"data\":\"c\""));
    }
}
