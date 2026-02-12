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

public class EventLedgerMemoryCacheTest extends EventLedgerTestBase {

    @Test
    public void testMemoryCacheEnabled() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();
        ledger.enableMemoryCache();

        ledger.writeEvent(createData("val", 1), Instant.now());

        Map<String, Object> health = ledger.healthCheck();
        Assert.assertEquals(health.get("memoryEnabled"), true);
        Assert.assertEquals(health.get("memoryCacheSize"), 1);

        // Read should come from memory (verified by not touching file if we could mock file access, 
        // but here we verify behavior consistency)
        try (Stream<BaseEvent> stream = ledger.readEvents(null)) {
            List<BaseEvent> events = stream.collect(Collectors.toList());
            Assert.assertEquals(events.size(), 1);
        }
    }

    @Test
    public void testHydrate() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();
        
        Instant now = Instant.now();
        ledger.writeEvent(createData("val", 1), now.minusSeconds(10));
        ledger.writeEvent(createData("val", 2), now);

        // Create new ledger instance to simulate restart
        ledger.stop();
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        // Hydrate only recent events
        int count = ledger.hydrate(now.minusSeconds(5));
        Assert.assertEquals(count, 1); // Only the second event

        Map<String, Object> health = ledger.healthCheck();
        Assert.assertEquals(health.get("memoryEnabled"), true);
        Assert.assertEquals(health.get("memoryCacheSize"), 1);
        
        Optional<BaseEvent> latest = ledger.getLatestEvent();
        Assert.assertTrue(latest.isPresent());
        Assert.assertEquals(latest.get().getData().get("val"), 2.0);
    }

    @Test
    public void testCleanupMemory() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();
        ledger.enableMemoryCache();

        Instant now = Instant.now();
        ledger.writeEvent(createData("val", 1), now.minusSeconds(100));
        ledger.writeEvent(createData("val", 2), now);

        int removed = ledger.cleanupMemory(now.minusSeconds(50));
        Assert.assertEquals(removed, 1);

        Map<String, Object> health = ledger.healthCheck();
        Assert.assertEquals(health.get("memoryCacheSize"), 1);
        
        try (Stream<BaseEvent> stream = ledger.readEvents(null)) {
            Assert.assertEquals(stream.count(), 1);
        }
    }
}
