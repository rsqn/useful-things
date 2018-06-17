package tech.rsqn.cacheservice.interceptors;

import tech.rsqn.cacheservice.TransparentCacheService;
import tech.rsqn.cacheservice.support.DelimitedKey;

import org.aopalliance.intercept.MethodInvocation;
import tech.rsqn.useful.things.reflection.ReflectionHelper;

import java.lang.reflect.Method;



public class InterceptorUtil {

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
