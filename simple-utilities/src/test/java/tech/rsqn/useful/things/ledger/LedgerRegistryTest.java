package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LedgerRegistryTest {
    private Path tempDir;
    private ExecutorService executor;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("registry-test");
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterMethod
    public void tearDown() throws IOException {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    @Test
    public void testRegistryInitialization() {
        LedgerRegistry registry = new LedgerRegistry();
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);
        registry.registerRecordType(TestRecord.TYPE, TestRecord.class);

        Ledger<TestRecord> priceLedger = registry.getLedger(TestRecord.TYPE);
        Assert.assertNotNull(priceLedger);
        
        // Register generic record type for system event if needed, or just use Record
        // Assuming RecordType.of("system_event") is valid and we can use Record.class or a subclass
        // For this test, let's just use TestRecord type again or another type if we had one.
        // Or register Record.class for system_event
        RecordType systemType = RecordType.of("system_event");
        registry.registerRecordType(systemType, Record.class);
        
        Ledger<Record> systemLedger = registry.getLedger(systemType);
        Assert.assertNotNull(systemLedger);
        Assert.assertNotSame(priceLedger, systemLedger);

        Collection<Ledger<?>> allLedgers = registry.getAllLedgers();
        Assert.assertEquals(allLedgers.size(), 2); // Only created 2
        Assert.assertTrue(allLedgers.contains(priceLedger));
        Assert.assertTrue(allLedgers.contains(systemLedger));
    }

    @Test
    public void testDefaultExecutor() {
        LedgerRegistry registry = new LedgerRegistry();
        registry.setLedgerDir(tempDir);
        registry.registerRecordType(TestRecord.TYPE, TestRecord.class);
        
        Ledger<TestRecord> ledger = registry.getLedger(TestRecord.TYPE);
        Assert.assertNotNull(ledger);
    }
}
