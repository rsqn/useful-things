package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EventLedgerWriteReadTest extends EventLedgerTestBase {

    @Test
    public void testWriteAndReadEvents() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        Instant now = Instant.now();
        ledger.writeEvent(createData("price", 100.0), now);
        ledger.writeEvent(createData("price", 101.0), now.plusSeconds(1));

        try (Stream<BaseEvent> stream = ledger.readEvents(null)) {
            List<BaseEvent> events = stream.collect(Collectors.toList());
            Assert.assertEquals(events.size(), 2);
            Assert.assertEquals(events.get(0).getData().get("price"), 100.0);
            Assert.assertEquals(events.get(1).getData().get("price"), 101.0);
            Assert.assertEquals(events.get(0).getEventId(), Long.valueOf(1));
            Assert.assertEquals(events.get(1).getEventId(), Long.valueOf(2));
        }
    }

    @Test
    public void testReadEventsReverse() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        for (int i = 0; i < 5; i++) {
            ledger.writeEvent(createData("index", i), Instant.now());
        }

        try (Stream<BaseEvent> stream = ledger.readEventsReverse()) {
            List<BaseEvent> events = stream.collect(Collectors.toList());
            Assert.assertEquals(events.size(), 5);
            Assert.assertEquals(events.get(0).getData().get("index"), 4.0); // Gson numbers are doubles
            Assert.assertEquals(events.get(4).getData().get("index"), 0.0);
        }
    }

    @Test
    public void testReadEventsFiltered() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        ledger.writeEvent(createData("type", "A"), Instant.now());
        ledger.writeEvent(createData("type", "B"), Instant.now());
        ledger.writeEvent(createData("type", "A"), Instant.now());

        try (Stream<BaseEvent> stream = ledger.readEventsFiltered(e -> "B".equals(e.getData().get("type")))) {
            List<BaseEvent> events = stream.collect(Collectors.toList());
            Assert.assertEquals(events.size(), 1);
            Assert.assertEquals(events.get(0).getData().get("type"), "B");
        }
    }

    @Test
    public void testGetLatestEvent() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        ledger.writeEvent(createData("val", 1), Instant.now());
        ledger.writeEvent(createData("val", 2), Instant.now());

        Optional<BaseEvent> latest = ledger.getLatestEvent();
        Assert.assertTrue(latest.isPresent());
        Assert.assertEquals(latest.get().getData().get("val"), 2.0);
    }
    
    @Test
    public void testReadEmptyLedger() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();
        
        try (Stream<BaseEvent> stream = ledger.readEvents(null)) {
            Assert.assertEquals(stream.count(), 0);
        }
        
        try (Stream<BaseEvent> stream = ledger.readEventsReverse()) {
            Assert.assertEquals(stream.count(), 0);
        }
        
        Assert.assertFalse(ledger.getLatestEvent().isPresent());
    }
}
