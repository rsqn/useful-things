package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;

public class LedgerSequenceTest extends LedgerTestBase {

    @Test
    public void testSequenceRecovery() throws IOException {
        ledger = createLedger();

        ledger.write(createData("val", 1), Instant.now()); // ID 1
        ledger.write(createData("val", 2), Instant.now()); // ID 2
        try {
            ledger.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Restart ledger
        Ledger newLedger = createLedger();

        long id = newLedger.write(createData("val", 3), Instant.now());
        Assert.assertEquals(id, 3);
        
        try {
            newLedger.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
