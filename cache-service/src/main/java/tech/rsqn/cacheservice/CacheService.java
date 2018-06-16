/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice;

import tech.rsqn.cacheservice.support.CacheIteratorCallBack;

import java.io.Serializable;


/**
 * Author: mandrewes
 * Date: 15/06/11
 */
public interface CacheService {
    /**
     * adds or replaces an object in the cache
     *
     * @param key
     * @param value
     * @return the number of objects added
     */
    <V extends Serializable>  void put(String key, V value);

    /**
     * adds or replaces an object in the cache
     *
     * @param key
     * @param value
     * @param timeToLiveSeconds
     * @return the number of objects added
     */
    <V extends Serializable>  void putWithTTL(String key, V value, int timeToLiveSeconds);

    /**
     * Fetches an object from the cache with the key specified
     *
     * @param key
     * @return
     */
    <V extends Serializable> V get(String key);

    /**
     * Removes an object from the cache with the key specified
     *
     * @param key
     * @return the number of objects removed
     */
    int remove(String key);

    /**
     * Gives status as to whether an entry with the specified key exists in the cache
     *
     * @param key
     * @return
     */
    boolean containsKey(String key);

    /**
     * @param value
     * @return
     */
    <V> boolean containsValue(V value);

    /**
     * Allows iteration over the keys in the cache
     * <p/>
     * This method does not lock the cache and so it is possible that you will
     * receive a key which is no longer in the cache.
     *
     * @param callBack
     * @return
     */
    void iterateThroughKeys(CacheIteratorCallBack callBack);

    /**
     * Returns the number of entries in the cache
     *
     * @return
     */
    long count();

    /**
     * Clears the cache
     *
     * @return returns number of items removed
     */
    long clear();
}
