package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AssetClassTest {

    @Test
    public void testFromValue() {
        Assert.assertEquals(AssetClass.fromValue("cryptocurrency"), AssetClass.CRYPTOCURRENCY);
        Assert.assertEquals(AssetClass.fromValue("equity"), AssetClass.EQUITY);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownValue() {
        AssetClass.fromValue("unknown_asset_class");
    }

    @Test
    public void testGetValue() {
        Assert.assertEquals(AssetClass.CRYPTOCURRENCY.getValue(), "cryptocurrency");
    }
}
