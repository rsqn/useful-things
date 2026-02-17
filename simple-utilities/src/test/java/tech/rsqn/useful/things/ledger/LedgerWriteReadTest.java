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
        ledger.write(createData("price", 100.0), now);
        ledger.write(createData("price", 101.0), now.plusSeconds(1));

        List<BaseEvent> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });

        Assert.assertEquals(events.size(), 2);
        Assert.assertEquals(events.get(0).getData().get("price"), 100.0);
        Assert.assertEquals(events.get(1).getData().get("price"), 101.0);
        Assert.assertEquals(events.get(0).getEventId(), Long.valueOf(1));
        Assert.assertEquals(events.get(1).getEventId(), Long.valueOf(2));
    }

    @Test
    public void testReadEventsReverse() throws IOException {
        ledger = createLedger();

        for (int i = 0; i < 5; i++) {
            ledger.write(createData("index", i), Instant.now());
        }

        List<BaseEvent> events = new ArrayList<>();
        ledger.readReverse(-1, null, event -> {
            events.add(event);
            return true;
        });

        Assert.assertEquals(events.size(), 5);
        Assert.assertEquals(((Number)events.get(0).getData().get("index")).doubleValue(), 4.0); // Gson numbers are doubles
        Assert.assertEquals(((Number)events.get(4).getData().get("index")).doubleValue(), 0.0);
    }

    @Test
    public void testReadEventsFiltered() throws IOException {
        ledger = createLedger();

        ledger.write(createData("type", "A"), Instant.now());
        ledger.write(createData("type", "B"), Instant.now());
        ledger.write(createData("type", "A"), Instant.now());

        List<BaseEvent> events = new ArrayList<>();
        ledger.read(-1, e -> "B".equals(e.getData().get("type")), event -> {
            events.add(event);
            return true;
        });

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getData().get("type"), "B");
    }

    @Test
    public void testGetLatestEvent() throws IOException {
        ledger = createLedger();

        ledger.write(createData("val", 1), Instant.now());
        ledger.write(createData("val", 2), Instant.now());

        AtomicReference<BaseEvent> latest = new AtomicReference<>();
        ledger.readReverse(-1, null, event -> {
            latest.set(event);
            return false; // Stop after first
        });

        Assert.assertNotNull(latest.get());
        Assert.assertEquals(((Number)latest.get().getData().get("val")).doubleValue(), 2.0);
    }
    
    @Test
    public void testReadEmptyLedger() throws IOException {
        ledger = createLedger();
        
        List<BaseEvent> events = new ArrayList<>();
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
