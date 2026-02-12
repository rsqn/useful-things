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
    private MapLedgerConfig config;
    private ExecutorService executor;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("registry-test");
        config = new MapLedgerConfig();
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
        LedgerRegistry registry = new LedgerRegistry(config, tempDir, executor);

        EventLedger priceLedger = registry.getLedger(EventType.PRICE_UPDATE);
        Assert.assertNotNull(priceLedger);
        
        EventLedger systemLedger = registry.getLedger(EventType.SYSTEM_EVENT);
        Assert.assertNotNull(systemLedger);
        Assert.assertNotSame(priceLedger, systemLedger);

        Collection<EventLedger> allLedgers = registry.getAllLedgers();
        Assert.assertEquals(allLedgers.size(), EventType.values().length);
        Assert.assertTrue(allLedgers.contains(priceLedger));
        Assert.assertTrue(allLedgers.contains(systemLedger));
    }

    @Test
    public void testDefaultExecutor() {
        LedgerRegistry registry = new LedgerRegistry(config, tempDir);
        EventLedger ledger = registry.getLedger(EventType.PRICE_UPDATE);
        Assert.assertNotNull(ledger);
        // We can't easily verify the internal executor without reflection, but we verify it doesn't crash
    }
}
