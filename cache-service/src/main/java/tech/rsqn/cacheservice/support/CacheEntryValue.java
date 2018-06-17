package tech.rsqn.cacheservice.support;

public interface CacheEntryValue {

    <T> T getValue();


    <T> void setValue(T value);
}
