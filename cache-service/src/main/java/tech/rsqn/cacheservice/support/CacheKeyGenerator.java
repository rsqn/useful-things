/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.support;

import tech.rsqn.cacheservice.TransparentCacheService;

import java.util.List;


/**
 * Author: mandrewes
 * Date: 16/06/11

 */
public abstract class CacheKeyGenerator<T> {
    public boolean supportsEntity(Object entity) {
        return supportsClass(entity.getClass());
    }

    public boolean supportsClass(Class clazz) {
        List<Class> supported = getSupportedClasses();

        for (Class aClass : supported) {
            if (clazz.equals(aClass)) {
                return true;
            }
        }

        return false;
    }

    public abstract List<Class> getSupportedClasses();

    public abstract String generateKey(TransparentCacheService service,
        Class clazz, Object... params);

    public abstract String generateKey(T entity);
}
