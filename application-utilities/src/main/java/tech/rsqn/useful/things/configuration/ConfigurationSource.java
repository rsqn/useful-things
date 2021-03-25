package tech.rsqn.useful.things.configuration;

import java.util.List;
import java.util.Map;

public interface ConfigurationSource {

    /**
     * Indicates whether the property exists and is not empty
     *
     * @param name - property key
     * @return bool - property set
     */
    boolean hasValue(final String name);

    String getEnvironment();

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
