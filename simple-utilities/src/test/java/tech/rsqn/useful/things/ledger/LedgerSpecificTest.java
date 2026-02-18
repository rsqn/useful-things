package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(testFile, ledgerRegistry);
        driver.init();
        driver.start();
        MemoryLedger<TestRecord> ledger2 = new MemoryLedger<>(TestRecord.TYPE, driver, null, Executors.newCachedThreadPool());
        ledger2.init();

        // Write one successful event
        long id1 = ledger2.write(createRecord("v1", 1));
        Assert.assertTrue(id1 > 0);
        
        List<TestRecord> events = new ArrayList<>();
        ledger2.read(-1, null, event -> {
            events.add(event);
            return true;
        });
        Assert.assertEquals(events.size(), 1);

        // Close ledger2 to release file lock
        ledger2.close();

        // Create the third ledger instance BEFORE destroying the file
        // This ensures constructor (which reads file) succeeds
        driver = new DiskPersistenceDriver<>(testFile, ledgerRegistry);
        driver.init();
        driver.start();
        MemoryLedger<TestRecord> ledger3 = new MemoryLedger<>(TestRecord.TYPE, driver, null, Executors.newCachedThreadPool());
        ledger3.init();

        // Now destroy the file and make it a directory to force write failure
        Files.delete(testFile);
        Files.createDirectories(testFile); 

        try {
            // Try to write using ledger3
            // This should fail IO but MemoryLedger swallows it and keeps in memory
            long id2 = ledger3.write(createRecord("v2", 2));

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
