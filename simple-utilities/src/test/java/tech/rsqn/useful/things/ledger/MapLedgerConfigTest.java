package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class MapLedgerConfigTest {

    @Test
    public void testConfigValues() {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("intKey", "123");
        configMap.put("boolKey", "true");
        configMap.put("decimalKey", "123.45");
        configMap.put("stringKey", "value");

        MapLedgerConfig config = new MapLedgerConfig(configMap);

        Assert.assertEquals(config.getInt("intKey", 0), 123);
        Assert.assertEquals(config.getBoolean("boolKey", false), true);
        Assert.assertEquals(config.getDecimal("decimalKey", BigDecimal.ZERO), new BigDecimal("123.45"));
        Assert.assertEquals(config.getString("stringKey", "default"), "value");
    }

    @Test
    public void testDefaults() {
        MapLedgerConfig config = new MapLedgerConfig();

        Assert.assertEquals(config.getInt("missing", 10), 10);
        Assert.assertEquals(config.getBoolean("missing", true), true);
        Assert.assertEquals(config.getDecimal("missing", BigDecimal.ONE), BigDecimal.ONE);
        Assert.assertEquals(config.getString("missing", "default"), "default");
    }
}
