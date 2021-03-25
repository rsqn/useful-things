package tech.rsqn.useful.things;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.configuration.ConfigurationSource;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public abstract class ConfigurationSourceTest {

    private ConfigurationSource configurationSource;

    protected abstract ConfigurationSource getConfigurationSource();

    @BeforeClass
    public void init() {
        configurationSource = getConfigurationSource();
    }

    private void shouldBePresent(String key){
        Assert.assertTrue(configurationSource.asMap().containsKey(key));
    }

    private void shouldNotBePresent(String key){
        Assert.assertFalse(configurationSource.asMap().containsKey(key));
    }

    // String

    @Test
    public void testString() {
        final String key = "test.string";
        shouldBePresent(key);
        final String value = configurationSource.getStringValue(key);
        Assert.assertEquals(value, "testing");
    }

    @Test
    public void testUndefinedString() {
        final String key = "test.undefined";
        shouldNotBePresent(key);
        final String value = configurationSource.getStringValue(key);
        Assert.assertEquals(value, null);
    }

    @Test
    public void testEmptyString() {
        final String key = "test.empty";
        shouldBePresent(key);
        final String value = configurationSource.getStringValue(key);
        Assert.assertEquals(value, "");
    }

    @Test
    public void testUndefinedStringWithDefault() {
        final String key = "test.undefined";
        final String defaultString = UUID.randomUUID().toString();
        shouldNotBePresent(key);
        final String value = configurationSource.getStringValue(key, defaultString);
        Assert.assertEquals(value, defaultString);
    }

    @Test
    public void testEmptyStringWithDefault() {
        final String key = "test.empty";
        final String defaultString = UUID.randomUUID().toString();
        shouldBePresent(key);
        final String value = configurationSource.getStringValue(key, defaultString);
        Assert.assertEquals(value, defaultString);
    }

    // int

    @Test
    public void testValidIntParsing() {
        final String oneKey = "test.integer.one";
        shouldBePresent(oneKey);
        final int resolvedOneKey = configurationSource.getIntValue(oneKey);
        Assert.assertEquals(resolvedOneKey, 1);
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testEmptyIntParsing() {
        final String emptyKey = "test.empty";
        shouldBePresent(emptyKey);
        final int resolvedEmptyKeyInteger = configurationSource.getIntValue(emptyKey);
    }

    @Test
    public void testEmptyIntParsingWithDefault() {
        final int defaultNumber = getRandomNumberUsingInts(Integer.MIN_VALUE, Integer.MAX_VALUE);
        final String emptyKey = "test.empty";
        shouldBePresent(emptyKey);
        final Integer resolvedNoKey = configurationSource.getIntValue(emptyKey, defaultNumber);
        Assert.assertNotNull(resolvedNoKey);
        Assert.assertEquals(resolvedNoKey.intValue(), defaultNumber);
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testMissingIntParsing() {
        final String noKey = "test.integer.nonexistant";
        shouldNotBePresent(noKey);
        final int resolvedNoKey = configurationSource.getIntValue(noKey);
    }

    @Test
    public void testMissingIntParsingWithDefault() {
        final int defaultNumber = getRandomNumberUsingInts(Integer.MIN_VALUE, Integer.MAX_VALUE);
        final String noKey = "test.integer.nonexistant";
        shouldNotBePresent(noKey);
        final int resolvedNoKey = configurationSource.getIntValue(noKey, defaultNumber);
        Assert.assertEquals(resolvedNoKey, defaultNumber);
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testInvalidIntParsing() {
        final String invalidIntegerKey = "test.integer.invalid";
        shouldBePresent(invalidIntegerKey);
        final int resolvedInvalidInteger = configurationSource.getIntValue(invalidIntegerKey);
    }

    // Integer

    @Test
    public void testValidIntegerParsing() {
        final String oneKey = "test.integer.one";
        shouldBePresent(oneKey);
        final Integer resolvedOneKey = configurationSource.getIntegerValue(oneKey);
        Assert.assertNotNull(resolvedOneKey);
        Assert.assertEquals(resolvedOneKey.intValue(), 1);
    }

    @Test
    public void testEmptyIntegerParsing() {
        final String emptyKey = "test.empty";
        shouldBePresent(emptyKey);
        final Integer resolvedEmptyKeyInteger = configurationSource.getIntegerValue(emptyKey);
        Assert.assertNull(resolvedEmptyKeyInteger);
    }

    @Test
    public void testEmptyIntegerParsingWithDefault() {
        final int defaultNumber = getRandomNumberUsingInts(Integer.MIN_VALUE, Integer.MAX_VALUE);
        final String emptyKey = "test.empty";
        shouldBePresent(emptyKey);
        final Integer resolvedNoKey = configurationSource.getIntegerValue(emptyKey, defaultNumber);
        Assert.assertNotNull(resolvedNoKey);
        Assert.assertEquals(resolvedNoKey.intValue(), defaultNumber);
    }

    @Test
    public void testMissingIntegerParsing() {
        final String noKey = "test.integer.nonexistant";
        shouldNotBePresent(noKey);
        final Integer resolvedNoKey = configurationSource.getIntegerValue(noKey);
        Assert.assertNull(resolvedNoKey);
    }

    @Test
    public void testMissingIntegerParsingWithDefault() {
        final int defaultNumber = getRandomNumberUsingInts(Integer.MIN_VALUE, Integer.MAX_VALUE);
        final String noKey = "test.integer.nonexistant";
        shouldNotBePresent(noKey);
        final Integer resolvedNoKey = configurationSource.getIntegerValue(noKey, defaultNumber);
        Assert.assertNotNull(resolvedNoKey);
        Assert.assertEquals(resolvedNoKey.intValue(), defaultNumber);
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testInvalidIntegerParsing() {
        final String invalidIntegerKey = "test.integer.invalid";
        shouldBePresent(invalidIntegerKey);
        final Integer resolvedInvalidInteger = configurationSource.getIntegerValue(invalidIntegerKey);
    }

    // double

    @Test
    public void testValidDouble() {
        final String key = "test.double.valid";
        shouldBePresent(key);
        final double value = configurationSource.getDoubleValue(key, 999.99);
        Assert.assertEquals(value, 1.0);
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testInvalidDouble() {
        final String key = "test.double.invalid";
        shouldBePresent(key);
        final double value = configurationSource.getDoubleValue(key, 999.99);
    }

    @Test
    public void testDefaultDouble() {
        final String key = "test.empty";
        shouldBePresent(key);
        final double value = configurationSource.getDoubleValue(key, 999.99);
        Assert.assertEquals(value, 999.99);
    }

    // boolean

    @Test
    public void testTrueBoolean() {
        final String key = "test.boolean.true";
        shouldBePresent(key);
        final boolean value = configurationSource.getBoolValue(key);
        Assert.assertEquals(value, true);
    }

    @Test
    public void testFalseBoolean() {
        final String key = "test.boolean.false";
        shouldBePresent(key);
        final boolean value = configurationSource.getBoolValue(key);
        Assert.assertEquals(value, false);
    }

    @Test
    public void testEmptyBoolean() {
        final String key = "test.empty";
        shouldBePresent(key);
        final boolean value = configurationSource.getBoolValue(key);
        Assert.assertEquals(value, false);
    }

    @Test
    public void testUndefinedBoolean() {
        final String key = "test.boolean.undefined";
        shouldNotBePresent(key);
        final boolean value = configurationSource.getBoolValue(key);
        Assert.assertEquals(value, false);
    }

    @Test
    public void testInvalidBoolean() {
        final String key = "test.boolean.invalid";
        shouldBePresent(key);
        final boolean value = configurationSource.getBoolValue(key);
        Assert.assertEquals(value, false);
    }

    @Test
    public void testEmptyBooleanWithDefault() {
        final String key = "test.empty";
        shouldBePresent(key);
        final boolean value = configurationSource.getBoolValue(key, true);
        Assert.assertEquals(value, true);
    }

    @Test
    public void testUndeclaredBooleanWithDefault() {
        final String key = "test.boolean.undefined";
        shouldNotBePresent(key);
        final boolean value = configurationSource.getBoolValue(key, true);
        Assert.assertEquals(value, true);
    }

    @Test
    public void testInvalidBooleanWithDefault() {
        final String key = "test.boolean.invalid";
        shouldBePresent(key);
        final boolean value = configurationSource.getBoolValue(key, true);
        Assert.assertEquals(value, false);
    }

    // has value

    @Test
    public void testValueExists() {
        final String key = "test.boolean.false";
        shouldBePresent(key);
        final boolean hasValue = configurationSource.hasValue(key);
        Assert.assertTrue(hasValue);
    }

    @Test
    public void testValueNotExists() {
        final String key = "test.fake.property";
        shouldNotBePresent(key);
        final boolean hasValue = configurationSource.hasValue(key);
        Assert.assertFalse(hasValue);
    }

    @Test
    public void testValueEmpty() {
        final String key = "test.empty";
        shouldBePresent(key);
        final boolean hasValue = configurationSource.hasValue(key);
        // TODO: Not sure whether this is correct but this is the current functionality
        Assert.assertFalse(hasValue);
    }

    // set value

    @Test
    public void testShouldSetNewValueCorrectly() {
        final String key = "new.key";
        shouldNotBePresent(key);
        final String newValue = UUID.randomUUID().toString();
        configurationSource.setValue(key, newValue);
        shouldBePresent(key);
        final String foundValue = configurationSource.getStringValue(key);
        Assert.assertEquals(newValue, foundValue);
        final String anotherNewValue = UUID.randomUUID().toString();
        configurationSource.setValue(key, anotherNewValue);
        shouldBePresent(key);
        final String anotherFoundValue = configurationSource.getStringValue(key);
        Assert.assertEquals(anotherNewValue, anotherFoundValue);
    }

    // Integer Array

    @Test
    public void testValidIntArray() {
        final String key = "array.int.valid";
        shouldBePresent(key);
        final List<Integer> array = configurationSource.getIntArray(key, ",");
        Assert.assertTrue(array.containsAll(new HashSet<Integer>(){{
            add(1); add(2); add(3); add(4);
        }}));
        Assert.assertEquals(array.size(), 4);
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testInvalidIntArray() {
        final String key = "array.int.invalid";
        shouldBePresent(key);
        final List<Integer> array = configurationSource.getIntArray(key, ",");
    }

    @Test
    public void testEmptyIntArray() {
        final String key = "test.empty";
        shouldBePresent(key);
        final List<Integer> array = configurationSource.getIntArray(key, ",");
        Assert.assertEquals(array.size(), 0);
    }

    @Test
    public void testUndefinedIntArray() {
        final String key = "test.undefined";
        shouldNotBePresent(key);
        final List<Integer> array = configurationSource.getIntArray(key, ",");
        Assert.assertEquals(array.size(), 0);
    }

    // String Array

    @Test
    public void testStringArray() {
        final String key = "array.string";
        shouldBePresent(key);
        final List<String> array = configurationSource.getStringArray(key, ",");
        Assert.assertTrue(array.containsAll(new HashSet<String>(){{
            add("one"); add("two"); add("three"); add("four");
        }}));
        Assert.assertEquals(array.size(), 4);
    }

    @Test
    public void testEmptyStringArray() {
        final String key = "test.empty";
        shouldBePresent(key);
        final List<String> array = configurationSource.getStringArray(key, ",");
        Assert.assertEquals(array.size(), 1);
        Assert.assertTrue(array.containsAll(new HashSet<String>(){{
            add("");
        }}));
    }

    @Test
    public void testUndefinedStringArray() {
        final String key = "test.undefined";
        shouldNotBePresent(key);
        final List<String> array = configurationSource.getStringArray(key, ",");
        Assert.assertEquals(array.size(), 0);
    }

    // Test Unicode properties

    @Test
    public void testUnicode() {
        final String key = "unicode";
        shouldBePresent(key);
        final String unicode = configurationSource.getStringValue(key);
        Assert.assertEquals(unicode, "????????");
    }

    public int getRandomNumberUsingInts(int min, int max) {
        Random random = new Random();
        return random.ints(min, max)
            .findFirst()
            .getAsInt();
    }
}
