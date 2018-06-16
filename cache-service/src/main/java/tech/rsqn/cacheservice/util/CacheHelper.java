package tech.rsqn.cacheservice.util;

import tech.rsqn.cacheservice.CacheService;
import tech.rsqn.cacheservice.support.CacheIteratorCallBack;

import java.io.Serializable;

import java.util.HashSet;
import java.util.Set;


/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 16/03/12
 *
 * To change this template use File | Settings | File Templates.
 */
public class CacheHelper {
    /**
     * You should not do this, especially with a large cache.
     * That is why this functionality is not in the cacheService
     * @param cache
     * @return
     */
    public static Set getKeySet(CacheService cache) {
        final Set ret = new HashSet();

        cache.iterateThroughKeys(new CacheIteratorCallBack() {
                public boolean onCallBack(String cacheKey) {
                    ret.add(cacheKey);

                    return true;
                }
            });

        return ret;
    }
}
