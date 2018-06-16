/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice;

import tech.rsqn.cacheservice.interceptors.InterceptorMetadata;

import org.aopalliance.intercept.MethodInvocation;

import java.io.Serializable;

import java.util.List;
import java.util.Map;


/**
 * Author: mandrewes
 * Date: 16/06/11
 *
 * <p/>
 * <p/>
 * A transparent cache service is designed to be used in between or service layers and their consumers.
 * <p/>
 * Caching and invalidation happens transparently to the consumer based on the caching rules for supported types.
 * <p/>
 * Some implementations may contain multiple cached entries (such as in collections) of a single object. In this case
 * invalidation of a single key or entity may result in the invalidation of other cache entries.
 *
 * @author mandrewes
 */
public interface TransparentCacheService {
    /**
     * number of elements in the cache
     *
     * @return
     */
    long count();

    /**
     * clear the cache
     *
     * @return
     */
    long clear();

    <T extends Serializable> String generateCacheKey(T entity);

    /**
     * Generate a cache key from an entity type and a list of arguments
     *
     * @param clazz
     * @param arguments
     * @return
     */
    String generateCacheKey(Class clazz, Object... arguments);

    /**
    * Generate a cache key for a parameter type
    *
    * @param clazz
    * @param arguments
    * @return
    */
    String generateParameterKey(Object parameter);

    /**
     * @param invocation
     * @return
     * @throws Throwable
     */
    Object aroundWriteMethodInvocation(MethodInvocation invocation,
        InterceptorMetadata meta) throws Throwable;

    /**
     * @param invocation
     * @return
     * @throws Throwable
     */
    Object aroundReadMethodInvocation(MethodInvocation invocation,
        InterceptorMetadata meta) throws Throwable;

    /**
     * @param invocation
     * @return
     * @throws Throwable
     */
    Object aroundInvalidateMethodInvocation(MethodInvocation invocation,
        InterceptorMetadata meta) throws Throwable;

    /**
     * Return a list of supported types the cache knows about
     *
     * @return
     */
    List<Class> getSupportedTypes();

    /**
     * Allow direct interaction with underlying caches if you really need it
     * @return
     */
    Map<String, CacheService> getCaches();
}
