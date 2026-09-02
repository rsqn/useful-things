package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DiskPersistenceDriverPlainJsonlIntegrityTest extends LedgerTestBase {

    @Test
    public void start_rejectsIncompleteFinalLineWithoutTrailingNewline() throws Exception {
        Files.writeString(ledgerFile, "{\"type\":\"TEST\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":\"partial");

        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        try {
            driver.start();
            Assert.fail("expected start() to reject incomplete final JSONL line");
        } catch (Exception e) {
            String message = e.getMessage();
            Assert.assertTrue(message.contains("Incomplete final JSONL line"), message);
            Assert.assertTrue(message.contains(ledgerFile.toString()), message);
            Assert.assertTrue(message.contains("offset"), message);
        }
    }

    @Test
    public void start_rejectsMalformedFinalLineWithTrailingNewline() throws Exception {
        Files.writeString(ledgerFile, "{not-valid-json}\n");

        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        try {
            driver.start();
            Assert.fail("expected start() to reject malformed final JSONL line");
        } catch (Exception e) {
            String message = e.getMessage();
            Assert.assertTrue(message.contains("Malformed final JSONL line"), message);
            Assert.assertTrue(message.contains(ledgerFile.toString()), message);
            Assert.assertTrue(message.contains("offset"), message);
        }
    }

    @Test
    public void start_acceptsWellFormedJsonlFile() throws Exception {
        DiskPersistenceDriver<TestRecord> writer = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        writer.init();
        writer.start();
        writer.writeBatch(List.of(
                createRecord("a", 1),
                createRecord("b", 2)));
        writer.flush();
        writer.close();

        DiskPersistenceDriver<TestRecord> reader = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        reader.init();
        reader.start();

        List<Integer> reverse = new java.util.ArrayList<>();
        reader.readReverse(-1, record -> {
            reverse.add(record.getValue());
            return true;
        });
        Assert.assertEquals(reverse, List.of(2, 1));
        reader.close();
    }

    @Test
    public void reopenWriteBehindMemoryLedger_rejectsTruncatedFile() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(true);

        WriteBehindMemoryLedger<TestRecord> wb = openWriteBehind();
        wb.write(createRecord("ok", 1));
        wb.flush();
        wb.close();
        ledger = null;

        byte[] bytes = Files.readAllBytes(ledgerFile);
        Files.write(ledgerFile, java.util.Arrays.copyOf(bytes, bytes.length - 3));

        try {
            openWriteBehind();
            Assert.fail("expected reopen to reject truncated ledger file");
        } catch (Exception e) {
            String message = e.getMessage();
            if (e.getCause() != null) {
                message = e.getCause().getMessage();
            }
            Assert.assertTrue(message.contains("Incomplete final JSONL line"), message);
        }
    }

    private WriteBehindMemoryLedger<TestRecord> openWriteBehind() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setAutoFlush(ledgerRegistry.isDefaultAutoFlush());
        driver.init();
        driver.start();

        WriteBehindMemoryLedger<TestRecord> wb =
                new WriteBehindMemoryLedger<>(TestRecord.TYPE, driver, null);
        wb.setWriteQueueCapacity(1_000);
        wb.init();
        ledger = wb;
        return wb;
    }
}
