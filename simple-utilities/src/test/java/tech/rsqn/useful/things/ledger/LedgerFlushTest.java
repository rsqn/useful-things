package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class LedgerFlushTest extends LedgerTestBase {

    @Test
    public void testAutoFlush() throws IOException {
        ledgerRegistry.setDefaultAutoFlush(true);
        ledger = createLedger(); // MemoryLedger (sync)

        ledger.write(createRecord("val", 1));

        // Should be on disk immediately
        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 1);
    }

    @Test
    public void testManualFlush() throws IOException {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(100);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);
        ledger = createLedger(); // MemoryLedger (sync)

        ledger.write(createRecord("val", 1));

        // Should NOT be on disk yet (buffered)
        Assert.assertEquals(Files.size(ledgerFile), 0);

        ledger.flush();

        List<String> lines = Files.readAllLines(ledgerFile);
        Assert.assertEquals(lines.size(), 1);
    }
}
