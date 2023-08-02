
package tech.rsqn.cacheservice;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tech.rsqn.cacheservice.hashmapcache.ReferenceHashMapCacheService;
import tech.rsqn.cacheservice.support.CacheIteratorCallBack;


public class ReferenceHashMapCacheServiceSanityTest {
    private ReferenceHashMapCacheService service;

    @BeforeMethod
    public void setupCache() {
        service = new ReferenceHashMapCacheService();
    }

    @Test
    public void shouldHaveFiveHundredThousandEntries()
        throws Exception {
        int n = 500 * 1000;

        service.setMaxSize(1000000);

        for (int i = 0; i < n; i++) {
            String key = "key." + i;
            String value = "value." + i;
            service.put(key, value);
        }

        Assert.assertEquals(service.count(), n);
    }

    @Test
    public void shouldHonourPutTTL()
            throws Exception {

        service.putWithTTL("xKey","Allo",500);

        Assert.assertNotNull(service.get("xKey"));

        Thread.sleep(1050);
        Assert.assertNull(service.get("xKey"));
    }


    @Test
    public void shouldRemoveAllEntriesWithIterationCallBack()
        throws Exception {
        int n = 500;

        for (int i = 0; i < n; i++) {
            String key = "key." + i;
            String value = "value." + i;
            service.put(key, value);
        }

        Assert.assertEquals(service.count(), n);

        service.iterateThroughKeys(new CacheIteratorCallBack() {
                public boolean onCallBack(String cacheKey) {
                    service.remove(cacheKey);

                    return true;
                }
            });

        Assert.assertEquals(service.count(), 0);
    }

    @Test
    public void shouldClearCache() throws Exception {
        int n = 500;

        for (int i = 0; i < n; i++) {
            String key = "key." + i;
            String value = "value." + i;
            service.put(key, value);
        }

        Assert.assertEquals(service.count(), n);
        service.clear();
        Assert.assertEquals(service.count(), 0);
    }

    @Test
    public void shouldHaltIterationIfCallBackReturnsFalse()
        throws Exception {
        int n = 500;

        for (int i = 0; i < n; i++) {
            String key = "key." + i;
            String value = "value." + i;
            service.put(key, value);
        }

        Assert.assertEquals(service.count(), n);

        service.iterateThroughKeys(new CacheIteratorCallBack() {
                public boolean onCallBack(String cacheKey) {
                    if (service.count() == 250) {
                        return false;
                    }

                    service.remove(cacheKey);

                    return true;
                }
            });

        Assert.assertEquals(service.count(), 250);
    }

    @Test
    public void shouldNotThrowExceptionForRemoveOfNonExistantKey() {
        int retCode = service.remove("invalid");
        Assert.assertEquals(retCode, 0);

        service.put("valid", "abc");

        retCode = service.remove("valid");
        Assert.assertEquals(retCode, 1);
    }

    @Test
    public void shouldReturnPositiveResultForContainsKeyAndGetOfValidKey() {
        service.put("validKey", "abc");
        Assert.assertTrue(service.containsKey("validKey"));

        Assert.assertEquals(service.get("validKey"), "abc");
    }

    @Test
    public void shouldReturnNegativeResultForContainsKeyAndGetOfInvalidKey() {
        service.put("validKey", "abc");
        Assert.assertFalse(service.containsKey("invalidKey"));

        Assert.assertNull(service.get("invalidKey"));
    }

    @Test
    public void shouldReturnValidResponseForContainsValue() {
        service.put("validKey", "validValue");
        Assert.assertFalse(service.containsValue("invalidValue"));
        Assert.assertTrue(service.containsValue("validValue"));
    }
}
