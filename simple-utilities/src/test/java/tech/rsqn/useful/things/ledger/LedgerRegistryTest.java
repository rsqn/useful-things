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
        LedgerRegistry registry = new LedgerRegistry();
        registry.setConfig(config);
        registry.setLedgerDir(tempDir);
        registry.setSharedExecutor(executor);

        Ledger priceLedger = registry.getLedger(EventType.PRICE_UPDATE);
        Assert.assertNotNull(priceLedger);
        
        Ledger systemLedger = registry.getLedger(EventType.SYSTEM_EVENT);
        Assert.assertNotNull(systemLedger);
        Assert.assertNotSame(priceLedger, systemLedger);

        Collection<Ledger> allLedgers = registry.getAllLedgers();
        Assert.assertEquals(allLedgers.size(), 2); // Only created 2
        Assert.assertTrue(allLedgers.contains(priceLedger));
        Assert.assertTrue(allLedgers.contains(systemLedger));
    }

    @Test
    public void testDefaultExecutor() {
        LedgerRegistry registry = new LedgerRegistry();
        registry.setConfig(config);
        registry.setLedgerDir(tempDir);
        
        Ledger ledger = registry.getLedger(EventType.PRICE_UPDATE);
        Assert.assertNotNull(ledger);
    }
}
