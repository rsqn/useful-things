package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

public class LedgerFlushTest extends LedgerTestBase {

    @Test
    public void testAutoFlush() throws IOException {
        config.put("ledger.auto_flush", "true");
        ledger = createLedger(); // MemoryLedger (sync)

        ledger.write(createData("val", 1), Instant.now());

        // Should be on disk immediately
        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 1);
    }

    @Test
    public void testManualFlush() throws IOException {
        config.put("ledger.auto_flush", "false");
        config.put("ledger.flush_interval_writes", "100");
        config.put("ledger.flush_interval_seconds", "100.0");
        ledger = createLedger(); // MemoryLedger (sync)

        ledger.write(createData("val", 1), Instant.now());

        // Should NOT be on disk yet (buffered)
        Assert.assertEquals(Files.size(ledgerFile), 0);

        ledger.flush();

        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 1);
    }
}
