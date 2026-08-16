package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Proves ZstdLedgerIndex append path stays correct and does not rewrite the whole file
 * on every append (O(n^2) regression that made 10M ZSTD writes unusable).
 */
public class ZstdLedgerIndexTest {
    private Path tempDir;
    private Path ledgerFile;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("zstd-idx");
        ledgerFile = tempDir.resolve("ledger.jsonl.zst");
    }

    @AfterMethod
    public void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    public void appendEntries_manyBatches_roundTripsAndFileSizeIsLinear() throws Exception {
        ZstdLedgerIndex idx = new ZstdLedgerIndex(ledgerFile);
        final int batches = 200;
        final int perBatch = 50;
        long expectedEntries = 0;
        for (int b = 0; b < batches; b++) {
            java.util.ArrayList<ZstdLedgerIndex.Entry> batch = new java.util.ArrayList<>(perBatch);
            for (int i = 0; i < perBatch; i++) {
                long ordinal = expectedEntries + i;
                batch.add(new ZstdLedgerIndex.Entry(ordinal * 100L, ordinal * 10L));
            }
            idx.appendEntries(batch);
            expectedEntries += perBatch;

            long fileSize = Files.size(idx.getIndexPath());
            long expectedSize = ZstdLedgerIndex.HEADER_SIZE
                    + expectedEntries * (long) ZstdLedgerIndex.ENTRY_SIZE;
            Assert.assertEquals(fileSize, expectedSize,
                    "index file must grow linearly (append), not rewrite-amplify");
        }

        Assert.assertEquals(idx.size(), (int) expectedEntries);
        Assert.assertEquals(idx.get(0).frameFileOffset, 0L);
        Assert.assertEquals(idx.get((int) expectedEntries - 1).frameFileOffset,
                (expectedEntries - 1) * 100L);

        // Reload from disk
        ZstdLedgerIndex reloaded = new ZstdLedgerIndex(ledgerFile);
        Assert.assertEquals(reloaded.size(), (int) expectedEntries);
        Assert.assertEquals(reloaded.get(123).uncompressedOffset, 1230L);
    }

    @Test
    public void appendEntries_frameSizedBatches_completesQuickly() throws Exception {
        // Matches DiskPersistenceDriver: one appendEntries call per zstd frame (~hundreds of rows).
        ZstdLedgerIndex idx = new ZstdLedgerIndex(ledgerFile);
        final int total = 40_000;
        final int batchSize = 250;
        long start = System.nanoTime();
        for (int base = 0; base < total; base += batchSize) {
            java.util.ArrayList<ZstdLedgerIndex.Entry> batch = new java.util.ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                int ord = base + i;
                batch.add(new ZstdLedgerIndex.Entry(ord, ord));
            }
            idx.appendEntries(batch);
        }
        long ms = (System.nanoTime() - start) / 1_000_000L;
        Assert.assertEquals(idx.size(), total);
        // Full rewrite-per-append is many minutes at 40k; true append should finish in a few seconds.
        Assert.assertTrue(ms < 10_000,
                "40k entries in frame-sized batches took " + ms
                        + "ms — index rewrite amplification still present?");
    }

    @Test
    public void replaceAll_stillRewritesConsistently() throws Exception {
        ZstdLedgerIndex idx = new ZstdLedgerIndex(ledgerFile);
        idx.appendEntries(List.of(
                new ZstdLedgerIndex.Entry(1, 2),
                new ZstdLedgerIndex.Entry(3, 4)));
        idx.replaceAll(List.of(new ZstdLedgerIndex.Entry(9, 8)));
        Assert.assertEquals(idx.size(), 1);
        Assert.assertEquals(idx.get(0).frameFileOffset, 9L);

        ZstdLedgerIndex reloaded = new ZstdLedgerIndex(ledgerFile);
        Assert.assertEquals(reloaded.size(), 1);
        Assert.assertEquals(reloaded.get(0).uncompressedOffset, 8L);
    }
}
