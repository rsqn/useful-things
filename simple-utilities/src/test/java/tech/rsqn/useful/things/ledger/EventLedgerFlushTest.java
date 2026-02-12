package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

public class EventLedgerFlushTest extends EventLedgerTestBase {

    @Test
    public void testAutoFlush() throws IOException {
        config.put("ledger.auto_flush", "true");
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        ledger.writeEvent(createData("val", 1), Instant.now());

        // Should be on disk immediately
        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 1);
    }

    @Test
    public void testManualFlush() throws IOException {
        config.put("ledger.auto_flush", "false");
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        ledger.writeEvent(createData("val", 1), Instant.now());

        // Should NOT be on disk yet (buffered)
        // Note: BufferedWriter might flush if buffer is full, but 1 event is small
        // However, reading the file directly via Files.readAllLines might see empty if not flushed
        // But since we are in the same process, OS buffering might handle it? 
        // No, BufferedWriter buffers in Java heap.
        
        // Wait, if I read the file, I'm reading from disk.
        // If writer hasn't flushed, file on disk is empty.
        Assert.assertEquals(Files.size(ledgerFile), 0);

        ledger.forceFlush();

        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 1);
    }

    @Test
    public void testBulkWriteMode() throws IOException {
        config.put("ledger.auto_flush", "true"); // Even with auto-flush on
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        ledger.bulkWriteMode(() -> {
            ledger.writeEvent(createData("val", 1), Instant.now());
            try {
                // Should not be flushed yet
                Assert.assertEquals(Files.size(ledgerFile), 0);
            } catch (IOException e) {
                Assert.fail("IO Exception");
            }
        });

        // Should be flushed after block
        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 1);
    }
}
