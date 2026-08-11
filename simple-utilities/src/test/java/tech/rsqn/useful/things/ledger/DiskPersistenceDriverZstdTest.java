package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Critical-path tests for optional ZSTD disk ledger compression.
 */
public class DiskPersistenceDriverZstdTest {
    private Path tempDir;
    private LedgerRegistry registry;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("ledger-zstd");
        registry = new LedgerRegistry();
        registry.setLedgerDir(tempDir);
        registry.registerRecordType(TestRecord.TYPE, TestRecord.class);
    }

    @AfterMethod
    public void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private DiskPersistenceDriver<TestRecord> newZstdDriver(Path file, boolean autoFlush) throws IOException {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(file, registry);
        driver.setCompression(LedgerCompression.ZSTD);
        driver.setZstdLevel(3);
        driver.setAutoFlush(autoFlush);
        driver.setFlushIntervalWrites(1000);
        driver.setFlushIntervalSeconds(60);
        driver.setZstdFrameFlushBytes(1_048_576);
        driver.init();
        driver.start();
        return driver;
    }

    private DiskPersistenceDriver<TestRecord> newPlainDriver(Path file) throws IOException {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(file, registry);
        driver.setCompression(LedgerCompression.NONE);
        driver.setAutoFlush(true);
        driver.init();
        driver.start();
        return driver;
    }

    private TestRecord rec(int value) {
        TestRecord r = new TestRecord(Instant.parse("2020-01-01T00:00:00Z"), "payload-" + value, value);
        r.setSequenceId((long) value);
        return r;
    }

    private List<TestRecord> readAll(DiskPersistenceDriver<TestRecord> driver) {
        List<TestRecord> out = new ArrayList<>();
        driver.read(-1, r -> {
            out.add(r);
            return true;
        });
        return out;
    }

    @Test
    public void unit_zstdRoundTrip_matchesPlainLogicalRecords() throws Exception {
        Path plainFile = tempDir.resolve("plain.jsonl");
        Path zstdFile = tempDir.resolve("comp.jsonl.zst");

        DiskPersistenceDriver<TestRecord> plain = newPlainDriver(plainFile);
        DiskPersistenceDriver<TestRecord> zstd = newZstdDriver(zstdFile, false);
        for (int i = 1; i <= 20; i++) {
            plain.write(rec(i));
            zstd.write(rec(i));
        }
        plain.flush();
        zstd.flush();
        plain.close();
        zstd.close();

        DiskPersistenceDriver<TestRecord> plainR = newPlainDriver(plainFile);
        DiskPersistenceDriver<TestRecord> zstdR = newZstdDriver(zstdFile, false);
        List<TestRecord> plainRecords = readAll(plainR);
        List<TestRecord> zstdRecords = readAll(zstdR);
        Assert.assertEquals(zstdRecords.size(), plainRecords.size());
        for (int i = 0; i < plainRecords.size(); i++) {
            Assert.assertEquals(zstdRecords.get(i).getValue(), plainRecords.get(i).getValue());
            Assert.assertEquals(zstdRecords.get(i).getData(), plainRecords.get(i).getData());
        }
        plainR.close();
        zstdR.close();
    }

    @Test
    public void integration_zstdMultiFrameAppendReopen_readsAll() throws Exception {
        Path file = tempDir.resolve("multi.jsonl.zst");
        DiskPersistenceDriver<TestRecord> d1 = newZstdDriver(file, false);
        d1.write(rec(1));
        d1.write(rec(2));
        d1.flush(); // frame end
        d1.write(rec(3));
        d1.close(); // clean shutdown ends last frame

        DiskPersistenceDriver<TestRecord> d2 = newZstdDriver(file, false);
        d2.write(rec(4));
        d2.write(rec(5));
        d2.close();

        DiskPersistenceDriver<TestRecord> d3 = newZstdDriver(file, false);
        List<Integer> values = new ArrayList<>();
        d3.read(-1, r -> {
            values.add(r.getValue());
            return true;
        });
        Assert.assertEquals(values, List.of(1, 2, 3, 4, 5));
        Assert.assertEquals(d3.count(), 5);
        d3.close();
    }

    @Test
    public void integration_zstdTruncatedFrame_readThrows() throws Exception {
        Path file = tempDir.resolve("trunc.jsonl.zst");
        DiskPersistenceDriver<TestRecord> d = newZstdDriver(file, false);
        for (int i = 1; i <= 5; i++) {
            d.write(rec(i));
        }
        d.close();

        byte[] bytes = Files.readAllBytes(file);
        Assert.assertTrue(bytes.length > 8);
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length / 2));

        DiskPersistenceDriver<TestRecord> broken = new DiskPersistenceDriver<>(file, registry);
        broken.setCompression(LedgerCompression.ZSTD);
        broken.init();
        try {
            broken.start();
            Assert.fail("expected start() to fail on truncated frame");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("trunc")
                    || expected.getCause() != null
                    || expected.getMessage().toLowerCase().contains("corrupt"));
        }

        // Forward read without successful start/rebuild must also fail loud
        DiskPersistenceDriver<TestRecord> reader = new DiskPersistenceDriver<>(file, registry);
        reader.setCompression(LedgerCompression.ZSTD);
        reader.init();
        try {
            reader.read(-1, r -> true);
            Assert.fail("expected read to fail");
        } catch (UncheckedIOException | IllegalStateException expected) {
            // fail loud
        }
    }

    @Test
    public void integration_zstdFlushThenReopen_readsFlushedRecords() throws Exception {
        Path file = tempDir.resolve("flush.jsonl.zst");
        DiskPersistenceDriver<TestRecord> d = newZstdDriver(file, false);
        d.write(rec(1));
        d.write(rec(2));
        d.flush();
        // Simulate abandoning in-memory compressor without close by discarding reference after flush
        d.close();

        DiskPersistenceDriver<TestRecord> r = newZstdDriver(file, false);
        Assert.assertEquals(readAll(r).size(), 2);
        r.close();
    }

    @Test
    public void integration_zstdRepetitiveJsonl_smallerThanPlain() throws Exception {
        Path plainFile = tempDir.resolve("rep.jsonl");
        Path zstdFile = tempDir.resolve("rep.jsonl.zst");
        DiskPersistenceDriver<TestRecord> plain = newPlainDriver(plainFile);
        DiskPersistenceDriver<TestRecord> zstd = newZstdDriver(zstdFile, false);
        for (int i = 0; i < 200; i++) {
            TestRecord r = new TestRecord(Instant.parse("2020-01-01T00:00:00Z"),
                    "AAAA_REPETITIVE_ORDER_BOOK_SNAPSHOT_PAYLOAD", i);
            r.setSequenceId((long) i);
            plain.write(r);
            zstd.write(r);
        }
        plain.close();
        zstd.close();
        long plainSize = Files.size(plainFile);
        long zstdSize = Files.size(zstdFile);
        Assert.assertTrue(zstdSize < plainSize,
                "expected compressed size " + zstdSize + " < plain " + plainSize);
    }

    @Test
    public void integration_zstdReadReverseAndCount() throws Exception {
        Path file = tempDir.resolve("rev.jsonl.zst");
        DiskPersistenceDriver<TestRecord> d = newZstdDriver(file, false);
        for (int i = 1; i <= 10; i++) {
            d.write(rec(i));
        }
        d.close();

        DiskPersistenceDriver<TestRecord> r = newZstdDriver(file, false);
        Assert.assertEquals(r.count(), 10);
        List<Integer> reverse = new ArrayList<>();
        r.readReverse(-1, rec -> {
            reverse.add(rec.getValue());
            return true;
        });
        Assert.assertEquals(reverse, List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1));
        r.close();
    }

    @Test
    public void integration_zstdSeekFrameRead_manySmallFrames_reverseOk() throws Exception {
        Path file = tempDir.resolve("tiny-frames.jsonl.zst");
        DiskPersistenceDriver<TestRecord> d = new DiskPersistenceDriver<>(file, registry);
        d.setCompression(LedgerCompression.ZSTD);
        d.setZstdLevel(3);
        d.setAutoFlush(false);
        d.setFlushIntervalWrites(1); // frame per write
        d.setZstdFrameFlushBytes(16); // force small frames
        d.init();
        d.start();
        for (int i = 1; i <= 30; i++) {
            d.write(rec(i));
        }
        d.close();

        DiskPersistenceDriver<TestRecord> r = newZstdDriver(file, false);
        List<Integer> reverse = new ArrayList<>();
        r.readReverse(-1, rec -> {
            reverse.add(rec.getValue());
            return reverse.size() < 5;
        });
        Assert.assertEquals(reverse, List.of(30, 29, 28, 27, 26));
        Assert.assertEquals(r.count(), 30);
        r.close();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void unit_zstdWriteBeforeStart_throws() throws Exception {
        Path file = tempDir.resolve("nostart.jsonl.zst");
        DiskPersistenceDriver<TestRecord> d = new DiskPersistenceDriver<>(file, registry);
        d.setCompression(LedgerCompression.ZSTD);
        d.init();
        d.write(rec(1));
    }

    @Test
    public void unit_noneDefault_unchangedRoundTrip() throws Exception {
        Path file = tempDir.resolve("default.jsonl");
        DiskPersistenceDriver<TestRecord> d = newPlainDriver(file);
        Assert.assertEquals(d.getCompression(), LedgerCompression.NONE);
        d.write(rec(1));
        d.close();
        DiskPersistenceDriver<TestRecord> r = newPlainDriver(file);
        Assert.assertEquals(readAll(r).size(), 1);
        Assert.assertFalse(DiskPersistenceDriver.fileStartsWithZstdMagic(file));
        r.close();
    }

    @Test
    public void integration_zstdWriteFlushConcurrent_noCorruptFrames() throws Exception {
        Path file = tempDir.resolve("concurrent.jsonl.zst");
        DiskPersistenceDriver<TestRecord> d = newZstdDriver(file, false);
        AtomicInteger written = new AtomicInteger();
        Thread writer = new Thread(() -> {
            try {
                for (int i = 1; i <= 50; i++) {
                    d.write(rec(i));
                    written.incrementAndGet();
                    if (i % 7 == 0) {
                        d.flush();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        writer.start();
        writer.join();
        d.close();

        DiskPersistenceDriver<TestRecord> r = newZstdDriver(file, false);
        Assert.assertEquals(r.count(), 50);
        Assert.assertEquals(readAll(r).size(), 50);
        r.close();
    }
}
