package tech.rsqn.cacheservice.support;

import org.testng.Assert;

import org.testng.annotations.Test;

public class DelimitedKeyTest {
    @Test
    public void shouldBuildKeyFromTwoStringsAndOneIntegerAndOneBooleanPrimitiveType()
        throws Exception {
        DelimitedKey key = DelimitedKey.with("one").and("two").and(1).and(true);

        Assert.assertEquals(".", DelimitedKey.delimiter);
        Assert.assertEquals(key.toString(), "one.two.1.true");
    }
}
