package tech.rsqn.useful.things.ledger;

/**
 * Asset classes supported by the trading system.
 */
public enum AssetClass {
    CRYPTOCURRENCY("cryptocurrency"),
    EQUITY("equity"),
    FUTURE("future");

    private final String value;

    AssetClass(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AssetClass fromValue(String value) {
        for (AssetClass type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AssetClass: " + value);
    }
}
