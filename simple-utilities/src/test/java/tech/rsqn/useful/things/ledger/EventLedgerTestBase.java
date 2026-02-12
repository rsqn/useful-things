package tech.rsqn.useful.things.ledger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public abstract class EventLedgerTestBase {
    protected Path tempDir;
    protected Path ledgerFile;
    protected MapLedgerConfig config;
    protected EventLedger ledger;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("ledger-test");
        ledgerFile = tempDir.resolve("test.jsonl");
        config = new MapLedgerConfig();
        // Default config for tests
        config.put("ledger.flush_interval_writes", "5");
        config.put("ledger.flush_interval_seconds", "1.0");
        config.put("ledger.auto_flush", "true");
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

    protected Map<String, Object> createData(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }
}
