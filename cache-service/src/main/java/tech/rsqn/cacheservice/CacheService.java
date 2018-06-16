package tech.rsqn.cacheservice;

import tech.rsqn.cacheservice.support.CacheIteratorCallBack;

import java.io.Serializable;


public interface CacheService {

    <V extends Serializable>  void put(String key, V value);


    <V extends Serializable>  void putWithTTL(String key, V value, int timeToLiveSeconds);

    <V extends Serializable> V get(String key);

    int remove(String key);

    boolean containsKey(String key);

    <V> boolean containsValue(V value);

    void iterateThroughKeys(CacheIteratorCallBack callBack);

    long count();


    long clear();
}
