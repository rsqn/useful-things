/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.support;

import java.io.Serializable;


/**
 * @author mandrewes
 */
public class DefaultCacheEntryValue
        implements CacheEntryValue, Serializable {
    private String key;
    private Object value;
    private long inserted;
    private long timeToLiveSeconds;

    public static <T extends Serializable> DefaultCacheEntryValue with(String key,
            final Object v) {
        DefaultCacheEntryValue ret = new DefaultCacheEntryValue();
        ret.key = key;
        ret.value = v;
        return ret;
    }

    public DefaultCacheEntryValue andTimeToLiveSeconds(long ttl) {
        this.timeToLiveSeconds = ttl;
        inserted = System.currentTimeMillis();
        return this;
    }

    public String getKey() {
        return key;
    }

    public boolean isValid() {
        if (timeToLiveSeconds > 0) {
            return System.currentTimeMillis() < (inserted + timeToLiveSeconds * 1000);
        }
        return true;
    }

    @Override
    public <T> void setValue(T value) {
        this.value = value;
    }

    public final <T> T getValue() {
        return (T) value;
    }


    public long getInserted() {
        return inserted;
    }

    public void setInserted(long inserted) {
        this.inserted = inserted;
    }

    public long getTimeToLiveSeconds() {
        return timeToLiveSeconds;
    }

    public void setTimeToLiveSeconds(long timeToLiveSeconds) {
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }

        DefaultCacheEntryValue that = (DefaultCacheEntryValue) o;

        if ((value != null) ? (!value.equals(that.value)) : (that.value != null)) {
            return false;
        }

        return true;
    }

    @Override
    public final int hashCode() {
        return (value != null) ? value.hashCode() : 0;
    }
}
