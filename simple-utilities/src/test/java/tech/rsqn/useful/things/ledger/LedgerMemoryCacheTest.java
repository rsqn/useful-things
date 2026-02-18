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

        ledger.write(createRecord("val", 1));

        Map<String, Object> health = ledger.healthCheck();
        // MemoryLedger always has memory
        Assert.assertEquals(health.get("memorySize"), 1L);

        List<TestRecord> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 1);
    }

    @Test
    public void testHydrate() throws Exception {
        // First ledger to write data
        Ledger<TestRecord> ledger1 = createLedger();
        Instant now = Instant.now();
        ledger1.write(new TestRecord(now.minusSeconds(10), "val", 1));
        ledger1.write(new TestRecord(now, "val", 2));
        ledger1.close();

        // Second ledger with retention filter
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();
        MemoryLedger<TestRecord> memLedger = new MemoryLedger<>(TestRecord.TYPE, driver, 
                e -> !e.getTimestamp().isBefore(now.minusSeconds(5)), // Keep only recent
                Executors.newCachedThreadPool());
        memLedger.init();
        ledger = memLedger;

        // Hydrate happens in init/constructor
        
        Map<String, Object> health = ledger.healthCheck();
        Assert.assertEquals(health.get("memorySize"), 1L);
        
        List<TestRecord> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getValue(), 2);
    }

    @Test
    public void testHousekeeping() throws IOException {
        // Config for small memory
        ledgerRegistry.setDefaultPreferredMaxSize(1);
        
        ledger = createLedger(); // Uses config

        ledger.write(createRecord("val", 1));
        ledger.write(createRecord("val", 2));

        // Memory size might be 2 before housekeeping
        Map<String, Object> health = ledger.healthCheck();
        Assert.assertEquals(health.get("memorySize"), 2L);

        if (ledger instanceof MemoryLedger) {
            ((MemoryLedger<TestRecord>) ledger).housekeeping();
        }

        health = ledger.healthCheck();
        Assert.assertEquals(health.get("memorySize"), 1L);
        
        List<TestRecord> events = new ArrayList<>();
        ledger.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getValue(), 2); // Oldest removed
    }
}
