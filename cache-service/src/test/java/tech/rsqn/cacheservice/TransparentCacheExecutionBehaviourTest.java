package tech.rsqn.cacheservice;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tech.rsqn.cacheservice.support.CacheBehaviour;
import tech.rsqn.cacheservice.support.TransparentCacheExecutionBehaviour;


/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 16/03/12
 *
 * To change this template use File | Settings | File Templates.
 */
public class TransparentCacheExecutionBehaviourTest {
    @BeforeMethod
    public void setUp() throws Exception {
        TransparentCacheExecutionBehaviour.enableDefaultBehaviour();
    }

    @Test
    public void shouldSetClearCacheAfterRead() throws Exception {
        TransparentCacheExecutionBehaviour.clearCacheAfterReads();

        CacheBehaviour behaviour = TransparentCacheExecutionBehaviour.getBehaviour();

        Assert.assertTrue(behaviour.isClearCacheAfterRead());
    }

    @Test
    public void shouldSetReturnIfItemIsNotCached() throws Exception {
        TransparentCacheExecutionBehaviour.returnIfItemIsNotCached();

        CacheBehaviour behaviour = TransparentCacheExecutionBehaviour.getBehaviour();

        Assert.assertTrue(behaviour.isReturnIfItemIsNotCached());
    }

    @AfterMethod
    public void tearDown() throws Exception {
        TransparentCacheExecutionBehaviour.enableDefaultBehaviour();
    }
}
