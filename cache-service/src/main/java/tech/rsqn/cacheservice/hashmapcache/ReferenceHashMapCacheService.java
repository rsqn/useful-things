package tech.rsqn.cacheservice.hashmapcache;

import tech.rsqn.cacheservice.CacheService;
import tech.rsqn.cacheservice.support.CacheIteratorCallBack;
import tech.rsqn.cacheservice.support.DefaultCacheEntryValue;

import java.io.Serializable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Author: mandrewes
 * Date: 15/06/11
 */
public class ReferenceHashMapCacheService implements CacheService {

    private Map<String, DefaultCacheEntryValue> map;
    private int maxSize = 10000;
    private int trimTo = 5000;

    public ReferenceHashMapCacheService() {
        map = new HashMap<String, DefaultCacheEntryValue>();
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setTrimTo(int trimTo) {
        this.trimTo = trimTo;
    }

    private void houseKeep() {
        if (map.size() >= maxSize) {
            synchronized (map) {
                List<DefaultCacheEntryValue> values = new CopyOnWriteArrayList<>();
                values.addAll(map.values());

                for (DefaultCacheEntryValue value : values) {
                    if (!value.isValid()) {
                        remove(value.getKey());
                    }
                }


                values.clear();
                values.addAll(map.values());

                if (values.size() > trimTo) {
                    int target = values.size() - trimTo;
                    int c = 0;
                    for (DefaultCacheEntryValue value : values) {
                        remove(value.getKey());
                        if (c++ == target) {
                            break;
                        }
                    }
                }

            }
        }
    }

    @Override
    public <V> boolean containsValue(V value) {
        return map.containsValue(DefaultCacheEntryValue.with("", value));
    }

    public <V extends Serializable> void put(String key, V value) {
        houseKeep();
        map.put(key, DefaultCacheEntryValue.with(key, value));
    }

    @Override
    public <V extends Serializable> void putWithTTL(String key, V value, int timeToLiveSeconds) {
        houseKeep();
        map.put(key, DefaultCacheEntryValue.with(key, value).andTimeToLiveSeconds(timeToLiveSeconds));
    }

    public <V extends Serializable> V get(String key) {
        DefaultCacheEntryValue v = map.get(key);

        if (v != null) {
            if (v.isValid()) {
                return (V) v.getValue();
            } else {
                map.remove(key);
            }
        }

        return null;
    }


    public int remove(String key) {
        if (map.remove(key) != null) {
            return 1;
        }

        return 0;
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public void iterateThroughKeys(CacheIteratorCallBack callBack) {
        List<String> l = new ArrayList<String>();
        l.addAll(map.keySet());

        for (String t : l) {
            if (!callBack.onCallBack(t)) {
                break;
            }
        }
    }

    public long count() {
        return map.size();
    }

    public long clear() {
        long v = map.size();
        map.clear();

        return v;
    }
}
