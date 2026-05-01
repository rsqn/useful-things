package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Fast newline {@link DiskPersistenceDriver#count()} and eager {@link DiskLedger} size cache.
 */
public class DiskLedgerLineCountTest extends LedgerTestBase {

    private static long readLineCount(Path f) throws IOException {
        if (!Files.exists(f)) {
            return 0L;
        }
        long c = 0;
        try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            while (r.readLine() != null) {
                c++;
            }
        }
        return c;
    }

    @Test
    public void fastCountMatchesReadLine_forSeveralShapes() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = createDriver();
        try {
            Assert.assertEquals(driver.count(), 0L);
            Assert.assertEquals(readLineCount(ledgerFile), 0L);

            Files.writeString(ledgerFile, "a\n", StandardCharsets.UTF_8);
            Assert.assertEquals(driver.count(), readLineCount(ledgerFile));

            Files.writeString(ledgerFile, "single", StandardCharsets.UTF_8);
            Assert.assertEquals(driver.count(), readLineCount(ledgerFile));

            Files.writeString(ledgerFile, "x\n\ny\n", StandardCharsets.UTF_8);
            Assert.assertEquals(driver.count(), readLineCount(ledgerFile));

            byte[] bigLine = new byte[64 * 1024];
            for (int i = 0; i < bigLine.length; i++) {
                bigLine[i] = (byte) ('0' + (i % 10));
            }
            Files.write(ledgerFile, bigLine, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            Files.write(ledgerFile, "\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            Assert.assertEquals(driver.count(), readLineCount(ledgerFile));
        } finally {
            driver.close();
        }
    }

    @Test
    public void diskLedgerConstructorLoadsSizeFromDisk() throws Exception {
        DiskPersistenceDriver<TestRecord> seed = createDriver();
        try {
            seed.write(createRecord("seed-a", 1));
            seed.write(createRecord("seed-b", 2));
        } finally {
            seed.close();
        }

        DiskPersistenceDriver<TestRecord> driver = createDriver();
        try {
            DiskLedger<TestRecord> dl = new DiskLedger<>(TestRecord.TYPE, driver);
            this.ledger = dl;
            Assert.assertEquals(dl.size(), 2L);
            Assert.assertEquals(dl.size(), 2L, "second size() should stay cached");
        } finally {
            driver.close();
        }
    }

    @Test
    public void diskLedgerSizeTracksWrites() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = createDriver();
        try {
            DiskLedger<TestRecord> dl = new DiskLedger<>(TestRecord.TYPE, driver);
            this.ledger = dl;
            Assert.assertEquals(dl.size(), 0L);
            dl.write(createRecord("a", 1));
            dl.write(createRecord("b", 2));
            Assert.assertEquals(dl.size(), 2L);
            Assert.assertEquals(readLineCount(ledgerFile), 2L);
        } finally {
            driver.close();
        }
    }

    @Test
    public void writeBehindSizeAfterFlushMatchesDisk() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(false);
        ledgerRegistry.setDefaultFlushIntervalWrites(10_000);
        ledgerRegistry.setDefaultFlushIntervalSeconds(100.0);

        DiskPersistenceDriver<TestRecord> driver = createDriver();
        WriteBehindDiskLedger<TestRecord> wb = new WriteBehindDiskLedger<>(TestRecord.TYPE, driver);
        wb.setWriteQueueCapacity(50);
        wb.init();
        this.ledger = wb;

        wb.write(createRecord("a", 1));
        wb.write(createRecord("b", 2));
        Assert.assertEquals(wb.size(), 2L);
        Assert.assertEquals(readLineCount(ledgerFile), 2L);
    }

    @Test
    public void memoryLedgerHealthCheckExposesDiskLineCount() throws Exception {
        DiskPersistenceDriver<TestRecord> driver = createDriver();
        try {
            MemoryLedger<TestRecord> ml = new MemoryLedger<>(TestRecord.TYPE, driver, null);
            ml.setPreferredMaxSize(ledgerRegistry.getDefaultPreferredMaxSize());
            ml.setAlarmSize(ledgerRegistry.getDefaultAlarmSize());
            ml.init();
            this.ledger = ml;

            ml.write(createRecord("x", 1));
            var hc = ml.healthCheck();
            Assert.assertEquals(hc.get("diskLineCount"), 1L);
            Assert.assertEquals(ml.size(), 1L);
        } finally {
            driver.close();
        }
    }

    private DiskPersistenceDriver<TestRecord> createDriver() throws IOException {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setAutoFlush(ledgerRegistry.isDefaultAutoFlush());
        driver.setFlushIntervalWrites(ledgerRegistry.getDefaultFlushIntervalWrites());
        driver.setFlushIntervalSeconds(ledgerRegistry.getDefaultFlushIntervalSeconds());
        driver.init();
        driver.start();
        return driver;
    }
}
