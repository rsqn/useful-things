/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.support;

/**
 * Author: mandrewes
 * Date: 15/06/11
 */
public interface CacheEntryValue {

    <T> T getValue();


    <T> void setValue(T value);
}
