package tech.rsqn.cacheservice.noopcache;

import tech.rsqn.cacheservice.CacheService;
import tech.rsqn.cacheservice.support.CacheIteratorCallBack;

/**
 * Created by mandrewes on 23/01/14.
 */
public class NoOpCacheService
        implements CacheService {
    @Override
    public <V> void put(String key, V value) {

    }

    @Override
    public <V> void putWithTTL(String key, V value, long ttlMs) {

    }

    @Override
    public <V> V get(String  key) {
        return null;
    }

    @Override
    public int remove(String key) {
        return 0;
    }

    @Override
    public boolean containsKey(String key) {
        return false;
    }

    @Override
    public <V> boolean containsValue(V value) {
        return false;
    }

    @Override
    public void iterateThroughKeys(CacheIteratorCallBack callBack) {

    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public long clear() {
        return 0;
    }
}
