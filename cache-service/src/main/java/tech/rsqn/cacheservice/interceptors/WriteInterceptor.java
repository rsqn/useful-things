package tech.rsqn.cacheservice.interceptors;

import tech.rsqn.cacheservice.annotations.WriteOperation;

import org.aopalliance.intercept.MethodInvocation;

public class WriteInterceptor extends AbstractInterceptor {
    public Object invoke(MethodInvocation invocation) throws Throwable {
        InterceptorMetadata meta = InterceptorMetadata.with(this,
                resolveTarget(invocation, WriteOperation.class));

        return cacheService.aroundWriteMethodInvocation(invocation, meta);
    }
}
