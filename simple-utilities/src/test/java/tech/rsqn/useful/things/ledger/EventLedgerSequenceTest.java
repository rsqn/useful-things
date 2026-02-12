package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;

public class EventLedgerSequenceTest extends EventLedgerTestBase {

    @Test
    public void testSequenceRecovery() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        ledger.writeEvent(createData("val", 1), Instant.now()); // ID 1
        ledger.writeEvent(createData("val", 2), Instant.now()); // ID 2
        ledger.stop();

        // Restart ledger
        EventLedger newLedger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        newLedger.start();

        long id = newLedger.writeEvent(createData("val", 3), Instant.now());
        Assert.assertEquals(id, 3);
        
        newLedger.stop();
    }
}
