/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.interceptors;

import tech.rsqn.cacheservice.TransparentCacheService;
import tech.rsqn.cacheservice.support.DelimitedKey;
import tech.rsqn.cacheservice.support.ReflectionHelper;

import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;


/**
 * Author: mandrewes
 * Date: 21/06/11
 *
 * <p/>
 * <p/>
 * Provides shared functions for interceptors
 *
 * @author mandrewes
 */
public class InterceptorUtil {
    /**
     * Generates a cache key based on method invocation.
     * <p/>
     * Only primitive types and supported classes are supported as methods
     *
     * @param invocation
     * @return
     */
    public static String generateCacheKeyBasedOnMethodInvocation(
        TransparentCacheService cache, MethodInvocation invocation) {
        Object[] args = invocation.getArguments();
        Method targetMethod = invocation.getMethod();

        DelimitedKey builder = DelimitedKey.with(targetMethod.getDeclaringClass()
                                                             .getName())
                                           .and(targetMethod.getName());
        String recognisedTypeKey = null;

        for (Object arg : args) {
            if (ReflectionHelper.isPrimitiveOrStringOrWrapper(arg)) {
                builder = builder.and(arg.toString());
            } else if ((recognisedTypeKey = cache.generateParameterKey(arg)) != null) {
                builder = builder.and(recognisedTypeKey);
            } else {
                return null;
            }
        }

        return builder.toString();
    }
}
