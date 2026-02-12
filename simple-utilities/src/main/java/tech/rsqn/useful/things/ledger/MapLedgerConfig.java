package tech.rsqn.useful.things.ledger;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple Map-backed implementation of LedgerConfig.
 */
public class MapLedgerConfig implements LedgerConfig {
    private final Map<String, String> config;

    public MapLedgerConfig() {
        this(new HashMap<>());
    }

    public MapLedgerConfig(Map<String, String> config) {
        this.config = config;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String val = config.get(key);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public String getString(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String val = config.get(key);
        if (val == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val);
    }

    @Override
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String val = config.get(key);
        if (val == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public void put(String key, String value) {
        config.put(key, value);
    }
}
