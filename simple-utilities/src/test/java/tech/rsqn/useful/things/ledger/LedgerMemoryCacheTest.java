package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class LedgerMemoryCacheTest extends LedgerTestBase {

    @Test
    public void testMemoryCacheEnabled() throws IOException {
        ledger = createLedger();

        ledger.write(createData("val", 1), Instant.now());

        Map<String, Object> health = ledger.healthCheck();
        // MemoryLedger always has memory
        Assert.assertEquals(health.get("memorySize"), 1L);

        List<BaseEvent> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 1);
    }

    @Test
    public void testHydrate() throws Exception {
        // First ledger to write data
        Ledger ledger1 = createLedger();
        Instant now = Instant.now();
        ledger1.write(createData("val", 1), now.minusSeconds(10));
        ledger1.write(createData("val", 2), now);
        ledger1.close();

        // Second ledger with retention filter
        DiskPersistenceDriver driver = new DiskPersistenceDriver(ledgerFile, config);
        driver.start();
        ledger = new MemoryLedger(EventType.PRICE_UPDATE, driver, config, 
                e -> !e.getTimestamp().isBefore(now.minusSeconds(5)), // Keep only recent
                Executors.newCachedThreadPool());

        // Hydrate happens in constructor
        
        Map<String, Object> health = ledger.healthCheck();
        Assert.assertEquals(health.get("memorySize"), 1L);
        
        List<BaseEvent> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(((Number)events.get(0).getData().get("val")).doubleValue(), 2.0);
    }

    @Test
    public void testHousekeeping() throws IOException {
        // Config for small memory
        config.put("ledger.memory.preferred_max_size", "1");
        
        ledger = createLedger(); // Uses config

        ledger.write(createData("val", 1), Instant.now());
        ledger.write(createData("val", 2), Instant.now());

        // Memory size might be 2 before housekeeping
        Map<String, Object> health = ledger.healthCheck();
        Assert.assertEquals(health.get("memorySize"), 2L);

        if (ledger instanceof MemoryLedger) {
            ((MemoryLedger) ledger).housekeeping();
        }

        health = ledger.healthCheck();
        Assert.assertEquals(health.get("memorySize"), 1L);
        
        List<BaseEvent> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(((Number)events.get(0).getData().get("val")).doubleValue(), 2.0); // Oldest removed
    }
}
