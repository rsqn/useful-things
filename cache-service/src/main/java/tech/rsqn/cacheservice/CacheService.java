package tech.rsqn.cacheservice;

import tech.rsqn.cacheservice.support.CacheIteratorCallBack;


public interface CacheService {

    <V> void put(String key, V value);


    <V> void putWithTTL(String key, V value, long ttlMs);

    <V> V get(String key);

    int remove(String key);

    boolean containsKey(String key);

    <V> boolean containsValue(V value);

    void iterateThroughKeys(CacheIteratorCallBack callBack);

    long count();


    long clear();
}
