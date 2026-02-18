package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class LedgerSequenceTest extends LedgerTestBase {

    @Test
    public void testSequenceRecovery() throws IOException {
        ledger = createLedger();

        ledger.write(createRecord("val", 1)); // ID 1
        ledger.write(createRecord("val", 2)); // ID 2
        try {
            ledger.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Restart ledger
        Ledger<TestRecord> newLedger = createLedger();

        long id = newLedger.write(createRecord("val", 3));
        Assert.assertEquals(id, 3);
        
        try {
            newLedger.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
