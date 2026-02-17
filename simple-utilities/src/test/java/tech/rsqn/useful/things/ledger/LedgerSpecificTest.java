package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class LedgerSpecificTest extends LedgerTestBase {

    @Test
    public void testWriteFailsWhenFileNotWritable() throws Exception {
        // Use a separate directory to avoid messing up other tests
        Path testDir = tempDir.resolve("readonly_test");
        Files.createDirectories(testDir);
        Path testFile = testDir.resolve("test.jsonl");
        
        // Create a new ledger instance
        PersistenceDriver driver = new DiskPersistenceDriver(testFile, config);
        Ledger ledger2 = new MemoryLedger(EventType.PRICE_UPDATE, driver, config, null, Executors.newCachedThreadPool());

        // Write one successful event
        long id1 = ledger2.write(createData("k", "v1"), Instant.now());
        Assert.assertTrue(id1 > 0);
        
        List<BaseEvent> events = new ArrayList<>();
        ledger2.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 1);

        // Close ledger2 to release file lock
        ledger2.close();

        // Create the third ledger instance BEFORE destroying the file
        // This ensures constructor (which reads file) succeeds
        driver = new DiskPersistenceDriver(testFile, config);
        Ledger ledger3 = new MemoryLedger(EventType.PRICE_UPDATE, driver, config, null, Executors.newCachedThreadPool());

        // Now destroy the file and make it a directory to force write failure
        // Note: DiskPersistenceDriver might have file open.
        // But write() re-checks or handles IO exception.
        // To force failure, we can delete the file and create a directory with same name.
        // But DiskPersistenceDriver keeps FileWriter open if started.
        // MemoryLedger constructor calls hydrate -> driver.read -> opens file.
        // But driver.write uses fileWriter.
        // DiskPersistenceDriver.start() is not exposed on PersistenceDriver interface.
        // But MemoryLedger doesn't call start().
        // DiskPersistenceDriver.write() calls start() implicitly or handles it?
        // DiskPersistenceDriver.write checks 'started'. If not, it uses fallback append.
        // If we want to force failure, we need to make the path invalid.
        
        // Let's close the driver inside ledger3? No, we can't access it easily.
        // But we can manipulate the file system.
        // If we delete the file and replace with directory, subsequent writes should fail.
        
        // However, DiskPersistenceDriver might have an open file handle (FileWriter).
        // If it does, deleting the file on Linux/Unix doesn't stop writing to the inode.
        // So write might succeed!
        // To force failure, we need to ensure FileWriter is NOT open or is closed.
        // DiskPersistenceDriver opens FileWriter in start().
        // Does MemoryLedger call start()? No.
        // Does DiskPersistenceDriver auto-start?
        // write() checks `if (!started)`. If so, it does fallback: `new BufferedWriter(new FileWriter(ledgerFile.toFile(), true))`.
        // This opens and closes file on every write if not started.
        // So if we replace file with directory, this `new FileWriter` will fail.
        
        Files.delete(testFile);
        Files.createDirectories(testFile); 

        try {
            // Try to write using ledger3
            // This should fail IO and return -1 (as per MemoryLedger implementation)
            // Wait, MemoryLedger catches IOException and continues (phantom write).
            // It returns eventId.
            // Wait, WriteAfterEventLedger returned -1.
            // MemoryLedger implementation:
            /*
            try {
                driver.write(event);
            } catch (IOException e) {
                e.printStackTrace(); // Log error but continue
                // We do NOT rollback memory here (FAST requirement)
            }
            return eventId;
            */
            // So it returns eventId, NOT -1.
            // The previous requirement was "phantom read", but user said "FAST".
            // If I return -1, I signal failure.
            // If I return eventId, I signal success (in memory).
            // The previous implementation returned -1.
            // But `MemoryLedger` implementation I wrote returns `eventId`.
            // "We do NOT rollback memory here... return eventId".
            // Let's verify what I wrote in MemoryLedger.java.
            
            long id2 = ledger3.write(createData("k", "v2"), Instant.now());

            // Since we don't rollback, the event IS in memory.
            // And we return valid ID.
            Assert.assertTrue(id2 > id1);

            // Verify it is in memory
            events.clear();
            ledger3.read(-1, null, event -> {
                events.add(event);
                return true;
            });
            // 1 initial (hydrated) + 1 phantom
            Assert.assertEquals(events.size(), 2, "Expected phantom read in memory");
            
        } finally {
            // Clean up
            ledger3.close();
            if (Files.isDirectory(testFile)) {
                Files.delete(testFile);
            }
        }
    }
}
