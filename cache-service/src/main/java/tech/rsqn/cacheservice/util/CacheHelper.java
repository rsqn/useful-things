package tech.rsqn.cacheservice.util;

import tech.rsqn.cacheservice.CacheService;
import tech.rsqn.cacheservice.support.CacheIteratorCallBack;

import java.io.Serializable;

import java.util.HashSet;
import java.util.Set;



public class CacheHelper {

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
