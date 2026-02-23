package tech.rsqn.useful.things.ledger;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for Ledger instances.
 * Acts as a factory and cache for ledgers.
 * Also acts as a registry for RecordTypes.
 */
public class LedgerRegistry {
    private final Map<RecordType, Ledger<?>> ledgers = new ConcurrentHashMap<>();
    private final Map<RecordType, Class<? extends Record>> recordTypes = new ConcurrentHashMap<>();
    
    private Path ledgerDir;

    // Default configuration for ledgers
    private int defaultPreferredMaxSize = 10000;
    private int defaultAlarmSize = 100000;
    private boolean defaultAutoFlush = true;
    private int defaultFlushIntervalWrites = 5000;
    private double defaultFlushIntervalSeconds = 5.0;
    private Integer defaultNotificationCorePoolSize;
    private Integer defaultNotificationMaxPoolSize;
    private Integer defaultNotificationQueueCapacity;
    private Long defaultNotificationKeepAliveSeconds;

    public LedgerRegistry() {
    }

    public void setLedgerDir(Path ledgerDir) {
        this.ledgerDir = ledgerDir;
    }

    public void setDefaultPreferredMaxSize(int defaultPreferredMaxSize) {
        this.defaultPreferredMaxSize = defaultPreferredMaxSize;
    }

    public void setDefaultAlarmSize(int defaultAlarmSize) {
        this.defaultAlarmSize = defaultAlarmSize;
    }

    public void setDefaultAutoFlush(boolean defaultAutoFlush) {
        this.defaultAutoFlush = defaultAutoFlush;
    }

    public void setDefaultFlushIntervalWrites(int defaultFlushIntervalWrites) {
        this.defaultFlushIntervalWrites = defaultFlushIntervalWrites;
    }

    public void setDefaultFlushIntervalSeconds(double defaultFlushIntervalSeconds) {
        this.defaultFlushIntervalSeconds = defaultFlushIntervalSeconds;
    }

    public void setDefaultNotificationCorePoolSize(int defaultNotificationCorePoolSize) {
        this.defaultNotificationCorePoolSize = defaultNotificationCorePoolSize;
    }

    public void setDefaultNotificationMaxPoolSize(int defaultNotificationMaxPoolSize) {
        this.defaultNotificationMaxPoolSize = defaultNotificationMaxPoolSize;
    }

    public void setDefaultNotificationQueueCapacity(int defaultNotificationQueueCapacity) {
        this.defaultNotificationQueueCapacity = defaultNotificationQueueCapacity;
    }

    public void setDefaultNotificationKeepAliveSeconds(long defaultNotificationKeepAliveSeconds) {
        this.defaultNotificationKeepAliveSeconds = defaultNotificationKeepAliveSeconds;
    }

    public int getDefaultPreferredMaxSize() {
        return defaultPreferredMaxSize;
    }

    public int getDefaultAlarmSize() {
        return defaultAlarmSize;
    }

    public boolean isDefaultAutoFlush() {
        return defaultAutoFlush;
    }

    public int getDefaultFlushIntervalWrites() {
        return defaultFlushIntervalWrites;
    }

    public double getDefaultFlushIntervalSeconds() {
        return defaultFlushIntervalSeconds;
    }

    public void registerRecordType(RecordType type, Class<? extends Record> clazz) {
        recordTypes.put(type, clazz);
    }

    public Class<? extends Record> getRecordClass(RecordType type) {
        return recordTypes.get(type);
    }

    @SuppressWarnings("unchecked")
    public <T extends Record> Ledger<T> getLedger(RecordType type) {
        return (Ledger<T>) ledgers.computeIfAbsent(type, this::createLedger);
    }

    private Ledger<?> createLedger(RecordType type) {
        if (ledgerDir == null) {
            throw new IllegalStateException("LedgerRegistry not initialized: ledgerDir must be set");
        }

        String filename = type.getValue() + ".jsonl";
        Path ledgerFile = ledgerDir.resolve(filename);
        
        // Create driver
        DiskPersistenceDriver<Record> driver = new DiskPersistenceDriver<>(ledgerFile, this);
        driver.setAutoFlush(defaultAutoFlush);
        driver.setFlushIntervalWrites(defaultFlushIntervalWrites);
        driver.setFlushIntervalSeconds(defaultFlushIntervalSeconds);
        driver.init();

        try {
            driver.start();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to start ledger driver", e);
        }
        
        // Create ledger (WriteBehindMemoryLedger for FAST access)
        WriteBehindMemoryLedger<Record> ledger = new WriteBehindMemoryLedger<>(type, driver, null);
        ledger.setPreferredMaxSize(defaultPreferredMaxSize);
        ledger.setAlarmSize(defaultAlarmSize);
        if (defaultNotificationCorePoolSize != null) {
            ledger.setNotificationCorePoolSize(defaultNotificationCorePoolSize);
        }
        if (defaultNotificationMaxPoolSize != null) {
            ledger.setNotificationMaxPoolSize(defaultNotificationMaxPoolSize);
        }
        if (defaultNotificationQueueCapacity != null) {
            ledger.setNotificationQueueCapacity(defaultNotificationQueueCapacity);
        }
        if (defaultNotificationKeepAliveSeconds != null) {
            ledger.setNotificationKeepAliveSeconds(defaultNotificationKeepAliveSeconds);
        }
        ledger.init();
        
        return ledger;
    }

    public Collection<Ledger<?>> getAllLedgers() {
        return Collections.unmodifiableCollection(ledgers.values());
    }
    
    @PostConstruct
    public void init() {
        if (ledgerDir == null) {
            // Optional: log warning or throw exception if strict
        }
    }
}
