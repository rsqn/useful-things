package tech.rsqn.useful.things.ledger;

import java.math.BigDecimal;

/**
 * Configuration interface for the ledger system.
 */
public interface LedgerConfig {
    int getInt(String key, int defaultValue);
    String getString(String key, String defaultValue);
    boolean getBoolean(String key, boolean defaultValue);
    BigDecimal getDecimal(String key, BigDecimal defaultValue);
}
