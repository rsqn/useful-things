package tech.rsqn.cacheservice.support;

import java.io.Serializable;
import java.util.Objects;


public class DefaultCacheEntryValue
        implements CacheEntryValue, Serializable {
    private String key;
    private Object value;
    private long inserted;
    private long timeToLiveMilliSeconds;

    public static <T> DefaultCacheEntryValue with(String key,
            final Object v) {
        DefaultCacheEntryValue ret = new DefaultCacheEntryValue();
        ret.key = key;
        ret.value = v;
        return ret;
    }

    public DefaultCacheEntryValue andTimeToLiveMilliseconds(long ttl) {
        this.timeToLiveMilliSeconds = ttl;
        inserted = System.currentTimeMillis();
        return this;
    }

    public String getKey() {
        return key;
    }

    public boolean isValid() {
        if (timeToLiveMilliSeconds > 0) {
            return System.currentTimeMillis() < (inserted + timeToLiveMilliSeconds);
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

    public long getTimeToLiveMilliSeconds() {
        return timeToLiveMilliSeconds;
    }

    public void setTimeToLiveMilliSeconds(long timeToLiveMilliSeconds) {
        this.timeToLiveMilliSeconds = timeToLiveMilliSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultCacheEntryValue that = (DefaultCacheEntryValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public final int hashCode() {
        return (value != null) ? value.hashCode() : 0;
    }
}
