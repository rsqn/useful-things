package tech.rsqn.useful.things.ledger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.Executors;

public abstract class LedgerTestBase {
    protected Path tempDir;
    protected Path ledgerFile;
    protected LedgerRegistry ledgerRegistry;
    protected Ledger<TestRecord> ledger;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("ledger-test");
        ledgerFile = tempDir.resolve("test.jsonl");
        
        ledgerRegistry = new LedgerRegistry();
        ledgerRegistry.setLedgerDir(tempDir);
        ledgerRegistry.setDefaultPreferredMaxSize(100);
        ledgerRegistry.setDefaultAlarmSize(200);
        ledgerRegistry.setDefaultAutoFlush(true);
        ledgerRegistry.setDefaultFlushIntervalWrites(5);
        ledgerRegistry.setDefaultFlushIntervalSeconds(1.0);
        
        ledgerRegistry.registerRecordType(TestRecord.TYPE, TestRecord.class);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (ledger != null) {
            ledger.close();
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

    protected TestRecord createRecord(String data, int value) {
        return new TestRecord(Instant.now(), data, value);
    }

    protected Ledger<TestRecord> createLedger() {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setAutoFlush(ledgerRegistry.isDefaultAutoFlush());
        driver.setFlushIntervalWrites(ledgerRegistry.getDefaultFlushIntervalWrites());
        driver.setFlushIntervalSeconds(ledgerRegistry.getDefaultFlushIntervalSeconds());
        driver.init();
        try {
            driver.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        MemoryLedger<TestRecord> ledger = new MemoryLedger<>(TestRecord.TYPE, driver, null, Executors.newCachedThreadPool());
        ledger.setPreferredMaxSize(ledgerRegistry.getDefaultPreferredMaxSize());
        ledger.setAlarmSize(ledgerRegistry.getDefaultAlarmSize());
        ledger.init();
        return ledger;
    }
    
    protected Ledger<TestRecord> createWriteBehindLedger() {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setAutoFlush(ledgerRegistry.isDefaultAutoFlush());
        driver.setFlushIntervalWrites(ledgerRegistry.getDefaultFlushIntervalWrites());
        driver.setFlushIntervalSeconds(ledgerRegistry.getDefaultFlushIntervalSeconds());
        driver.init();
        try {
            driver.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        WriteBehindMemoryLedger<TestRecord> ledger = new WriteBehindMemoryLedger<>(TestRecord.TYPE, driver, null, Executors.newCachedThreadPool());
        ledger.setPreferredMaxSize(ledgerRegistry.getDefaultPreferredMaxSize());
        ledger.setAlarmSize(ledgerRegistry.getDefaultAlarmSize());
        ledger.init();
        return ledger;
    }
}
