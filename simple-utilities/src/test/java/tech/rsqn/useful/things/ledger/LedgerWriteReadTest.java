package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LedgerWriteReadTest extends LedgerTestBase {

    @Test
    public void testWriteAndReadEvents() throws IOException {
        ledger = createLedger();
        // No start() needed for Ledger interface

        Instant now = Instant.now();
        ledger.write(createRecord("price", 100));
        ledger.write(createRecord("price", 101));

        List<TestRecord> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });

        Assert.assertEquals(events.size(), 2);
        Assert.assertEquals(events.get(0).getValue(), 100);
        Assert.assertEquals(events.get(1).getValue(), 101);
        Assert.assertEquals(events.get(0).getSequenceId(), Long.valueOf(1));
        Assert.assertEquals(events.get(1).getSequenceId(), Long.valueOf(2));
    }

    @Test
    public void testReadEventsReverse() throws IOException {
        ledger = createLedger();

        for (int i = 0; i < 5; i++) {
            ledger.write(createRecord("index", i));
        }

        List<TestRecord> events = new ArrayList<>();
        ledger.readReverse(-1, null, event -> {
            events.add(event);
            return true;
        });

        Assert.assertEquals(events.size(), 5);
        Assert.assertEquals(events.get(0).getValue(), 4);
        Assert.assertEquals(events.get(4).getValue(), 0);
    }

    @Test
    public void testReadEventsFiltered() throws IOException {
        ledger = createLedger();

        ledger.write(createRecord("A", 1));
        ledger.write(createRecord("B", 2));
        ledger.write(createRecord("A", 3));

        List<TestRecord> events = new ArrayList<>();
        ledger.read(-1, e -> "B".equals(e.getData()), event -> {
            events.add(event);
            return true;
        });

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getData(), "B");
    }

    @Test
    public void testGetLatestEvent() throws IOException {
        ledger = createLedger();

        ledger.write(createRecord("val", 1));
        ledger.write(createRecord("val", 2));

        AtomicReference<TestRecord> latest = new AtomicReference<>();
        ledger.readReverse(-1, null, event -> {
            latest.set(event);
            return false; // Stop after first
        });

        Assert.assertNotNull(latest.get());
        Assert.assertEquals(latest.get().getValue(), 2);
    }
    
    @Test
    public void testReadEmptyLedger() throws IOException {
        ledger = createLedger();
        
        List<TestRecord> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 0);
        
        events.clear();
        ledger.readReverse(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 0);
    }
}
