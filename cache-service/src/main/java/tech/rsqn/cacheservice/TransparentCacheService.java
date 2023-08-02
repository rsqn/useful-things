package tech.rsqn.cacheservice;

import org.aopalliance.intercept.MethodInvocation;
import tech.rsqn.cacheservice.interceptors.InterceptorMetadata;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


public interface TransparentCacheService {

    long count();


    long clear();

    <T extends Serializable> String generateCacheKey(T entity);

    String generateCacheKey(Class clazz, Object... arguments);

    String generateParameterKey(Object parameter);

    Object aroundWriteMethodInvocation(MethodInvocation invocation,
        InterceptorMetadata meta) throws Throwable;

    Object aroundReadMethodInvocation(MethodInvocation invocation,
        InterceptorMetadata meta) throws Throwable;

    Object aroundInvalidateMethodInvocation(MethodInvocation invocation,
        InterceptorMetadata meta) throws Throwable;

    List<Class> getSupportedTypes();

    Map<String, CacheService> getCaches();
}
