/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.support;

import java.io.Serializable;


/**
 * Author: mandrewes
 * Date: 15/06/11
 *
 * <p/>
 * A container for values (not keys) in the cache, allowing storage of metadata with the
 * entry.
 * <p/>
 * it is not mandatory that cache implementations use a container for values, and as such this container
 * should never be handed out to clients of a cache service.
 *
 * @author mandrewes
 */
public interface CacheEntryValue {
    /**
     * @return
     */
    <T> T getValue();

    /**
     * @param value
     */
    <T> void setValue(T value);
}
