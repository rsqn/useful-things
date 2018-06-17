
package tech.rsqn.cacheservice.support;

import org.testng.Assert;

import org.testng.annotations.Test;
import tech.rsqn.useful.things.reflection.ReflectionHelper;

import java.util.HashMap;

public class ReflectionHelperTest {
    @Test
    public void shouldReturnFalseForMapOrObject() throws Exception {
        Assert.assertFalse(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new HashMap()));
        Assert.assertFalse(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new Object()));
    }

    @Test
    public void shouldReturnTrueForFloatTypes() throws Exception {
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new Float(1)));
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(1f));
    }

    @Test
    public void shouldReturnTrueForIntegerTypes() throws Exception {
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new Integer(1)));
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(1));
    }

    @Test
    public void shouldReturnTrueForCharacterTypes() throws Exception {
        char c = 1;
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new Character(c)));
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(c));
    }

    @Test
    public void shouldReturnTrueForByteTypes() throws Exception {
        byte b = 1;
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new Byte(b)));
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(b));
    }

    @Test
    public void shouldReturnTrueForShortTypes() throws Exception {
        short s = 1;
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new Short(s)));
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(s));
    }

    @Test
    public void shouldReturnTrueForDoubleTypes() throws Exception {
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new Double(1)));
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(1d));
    }

    @Test
    public void shouldReturnTrueForLongTypes() throws Exception {
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(
                new Long(1)));
        Assert.assertTrue(ReflectionHelper.isPrimitiveOrStringOrWrapper(1L));
    }
}
