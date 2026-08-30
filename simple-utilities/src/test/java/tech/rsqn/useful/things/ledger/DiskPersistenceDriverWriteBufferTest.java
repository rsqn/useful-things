package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;

public class DiskPersistenceDriverWriteBufferTest extends LedgerTestBase {

    @Test
    public void setWriteBufferChars_beforeStart_appliedOnStart() throws Exception {
        ledgerRegistry.setDefaultAutoFlush(true);
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.setWriteBufferChars(256 * 1024);
        driver.setReadBufferChars(128 * 1024);
        driver.init();
        driver.start();

        driver.write(createRecord("a", 1));
        driver.flush();
        driver.close();

        Assert.assertEquals(Files.readAllLines(ledgerFile).size(), 1);
        Assert.assertEquals(driver.getWriteBufferChars(), 256 * 1024);
        Assert.assertEquals(driver.getReadBufferChars(), 128 * 1024);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void setWriteBufferChars_afterStart_throws() throws IOException {
        DiskPersistenceDriver<TestRecord> driver = new DiskPersistenceDriver<>(ledgerFile, ledgerRegistry);
        driver.init();
        driver.start();
        driver.setWriteBufferChars(16_384);
    }
}
