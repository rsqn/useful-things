package tech.rsqn.useful.things.configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ConfigurationSource {

    /**
     * Indicates whether the property exists (is defined), regardless of whether the value is empty.
     *
     * @param name - property key
     * @return true if the property is defined (getStringValue returns non-null)
     */
    boolean containsProperty(final String name);

    /**
     * Indicates whether the property exists and has a non-empty value.
     *
     * @param name - property key
     * @return true if the property is defined and not null/empty
     */
    boolean hasValue(final String name);

    String getEnvironment();

    /**
     * Returns the string value for a property, throwing if missing.
     *
     * @param name - property key
     * @return String property value
     * @throws IllegalStateException if the property is undefined (null)
     */
    String requireString(final String name);

    /**
     * Returns the int value for a property, throwing if missing.
     *
     * @param name - property key
     * @return int property value
     * @throws IllegalStateException if the property is undefined (null)
     * @throws NumberFormatException if the value is not parsable as int
     */
    int requireInt(final String name);

    /**
     * Returns the long value for a property, throwing if missing.
     *
     * @param name - property key
     * @return long property value
     * @throws IllegalStateException if the property is undefined (null)
     * @throws NumberFormatException if the value is not parsable as long
     */
    long requireLong(final String name);

    /**
     * Returns the double value for a property, throwing if missing.
     *
     * @param name - property key
     * @return double property value
     * @throws IllegalStateException if the property is undefined (null)
     * @throws NumberFormatException if the value is not parsable as double
     */
    double requireDouble(final String name);

    /**
     * Returns the boolean value for a property, throwing if missing.
     *
     * @param name - property key
     * @return boolean property value
     * @throws IllegalStateException if the property is undefined (null)
     */
    boolean requireBoolean(final String name);

    /**
     * Returns the BigDecimal value for a property, throwing if missing.
     *
     * @param name - property key
     * @return BigDecimal property value
     * @throws IllegalStateException if the property is undefined (null)
     * @throws NumberFormatException if the value is not parsable as BigDecimal
     */
    BigDecimal requireBigDecimal(final String name);

    /**
     * Returns the string value for a property given the key.
     * Will return null if property value is undefined.
     *
     * @param name - property key
     * @return String property value
     */
    String getStringValue(final String name);

    /**
     * Returns the string value for a property given the key.
     * Will return default value if property value is undefined or is empty.
     *
     * @param name - property key
     * @param dfl - default value
     * @return String property value
     */
    String getStringValue(final String name, final String dfl);

    /**
     * Given a property key, return an int value.
     * <p>
     * Will attempt to parse as an int.
     *
     * @param name - property key
     * @return Integer property value
     * @throws NumberFormatException - If the property value is empty or it does not contain a parsable Integer
     */
    int getIntValue(final String name);

    /**
     * Given a property key and default, return an Integer value.
     * <p>
     * Will attempt to parse as an Integer. An empty or undefined property will return the int provided.
     *
     * @param name - property key
     * @return Integer property value
     * @throws NumberFormatException - If the property value does not contain a parsable Integer
     */
    int getIntValue(final String name, int dfl);

    /**
     * Given a property key, return an Integer value.
     * <p>
     * If the property is present - Will attempt to parse as an Integer. An empty or undefined property will return null.
     *
     * @param name - property key
     * @return Integer property value
     * @throws NumberFormatException - If the property value does not contain a parsable Integer
     */
    Integer getIntegerValue(final String name);

    /**
     * Given a property key and default, return an Integer value.
     * <p>
     * If the property is present - Will attempt to parse as an Integer. An empty or undefined property will return the
     * Integer provided.
     *
     * @param name - property key
     * @return Integer property value
     * @throws NumberFormatException - If the property value does not contain a parsable Integer
     */
    Integer getIntegerValue(final String name, final Integer dfl);

    /**
     * Given a property key, return a Long value.
     * <p>
     * If the property is present - will attempt to parse as a Long. An empty or undefined property will return null.
     *
     * @param name - property key
     * @return Long property value
     * @throws NumberFormatException - If the property value does not contain a parsable Long
     */
    Long getLongValue(final String name);

    /**
     * Given a property key and default, return a Long value.
     * <p>
     * If the property is present - will attempt to parse as a Long. An empty or undefined property will return the default.
     *
     * @param name - property key
     * @param dfl  - default value
     * @return Long property value
     * @throws NumberFormatException - If the property value does not contain a parsable Long
     */
    Long getLongValue(final String name, final Long dfl);

    /**
     * Given a property key, return a BigDecimal value.
     * <p>
     * If the property is present - will attempt to parse as a BigDecimal. An empty or undefined property will return null.
     *
     * @param name - property key
     * @return BigDecimal property value
     * @throws NumberFormatException - If the property value does not contain a parsable BigDecimal
     */
    BigDecimal getBigDecimalValue(final String name);

    /**
     * Given a property key and default, return a BigDecimal value.
     * <p>
     * If the property is present - will attempt to parse as a BigDecimal. An empty or undefined property will return the default.
     *
     * @param name - property key
     * @param dfl  - default value
     * @return BigDecimal property value
     * @throws NumberFormatException - If the property value does not contain a parsable BigDecimal
     */
    BigDecimal getBigDecimalValue(final String name, final BigDecimal dfl);

    /**
     * Given a property key and default, return a Double value.
     * <p>
     * If the property is present - Will attempt to parse as a Double. An empty or undefined property will return the
     * default double provided.
     *
     * @param name - property key
     * @return double property value
     * @throws NumberFormatException - If the property value does not contain a parsable Double
     */
    double getDoubleValue(String name, double dfl);

    /**
     * Given a property key, return a boolean value.
     * <p>
     * Will return false if name is null or the property value does not represent a boolean.
     *
     * @param name - property key
     * @return boolean property value
     */
    boolean getBoolValue(final String name);

    /**
     * Given a property key and default, return a boolean value.
     * <p>
     * Will return default if name is null or the property value is null/empty.
     * Will return false if the property value does not represent a boolean.
     *
     * @param name - property key
     * @return boolean property value
     */
    boolean getBoolValue(final String name, boolean dfl);

    /**
     * Returns the property set as a string map.
     *
     * @return Map - All properties
     */
    Map<String, String> asMap();

    /**
     * Given property key and delimiter, return an Integer List
     *
     * Using the delimiter, we get the property value as an Integer List.
     * If the property is undefined or empty, an empty list is returned.
     *
     * @param name - property key
     * @param delimiter - delimiter used to separate Integers
     * @return List of Integers
     * @throws NumberFormatException - If the property value contains a non-parsable Integer
     */
    List<Integer> getIntArray(final String name, final String delimiter);

    /**
     * Given property key and delimiter, return an String List
     *
     * Using the delimiter, we get the property value as an String List.
     * If the property is undefined or empty, an empty list is returned.
     *
     * @param name - property key
     * @param delimiter - delimiter used to separate Strings
     * @return List of Strings
     */
    List<String> getStringArray(final String name, final String delimiter);

    /**
     * Sets a property value
     * @param name - property key
     * @param value - property value
     */
    void setValue(final String name, final String value);
}
