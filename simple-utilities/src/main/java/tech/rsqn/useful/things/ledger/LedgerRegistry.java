package tech.rsqn.useful.things.ledger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Registry for Ledger instances.
 * Acts as a factory and cache for ledgers.
 */
public class LedgerRegistry {
    private final Map<EventType, Ledger> ledgers = new ConcurrentHashMap<>();
    private LedgerConfig config;
    private Path ledgerDir;
    private ExecutorService sharedExecutor;

    public LedgerRegistry() {
    }

    public void setConfig(LedgerConfig config) {
        this.config = config;
    }

    public void setLedgerDir(Path ledgerDir) {
        this.ledgerDir = ledgerDir;
    }

    public void setSharedExecutor(ExecutorService sharedExecutor) {
        this.sharedExecutor = sharedExecutor;
    }

    public Ledger getLedger(EventType type) {
        return ledgers.computeIfAbsent(type, this::createLedger);
    }

    private Ledger createLedger(EventType type) {
        if (config == null || ledgerDir == null) {
            throw new IllegalStateException("LedgerRegistry not initialized: config and ledgerDir must be set");
        }

        if (sharedExecutor == null) {
            sharedExecutor = Executors.newCachedThreadPool();
        }

        String filename = type.getValue() + ".jsonl";
        Path ledgerFile = ledgerDir.resolve(filename);
        
        // Create driver
        DiskPersistenceDriver driver = new DiskPersistenceDriver(ledgerFile, config);
        try {
            driver.start();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to start ledger driver", e);
        }
        
        // Create ledger (WriteBehindMemoryLedger for FAST access)
        return new WriteBehindMemoryLedger(type, driver, config, null, sharedExecutor);
    }

    public Collection<Ledger> getAllLedgers() {
        return Collections.unmodifiableCollection(ledgers.values());
    }
}
