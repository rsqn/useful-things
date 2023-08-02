package tech.rsqn.cacheservice.interceptors;

import org.aopalliance.intercept.MethodInvocation;
import tech.rsqn.cacheservice.annotations.InvalidatingOperation;


public class InvalidatingInterceptor extends AbstractInterceptor {
    public Object invoke(MethodInvocation invocation) throws Throwable {
        InterceptorMetadata meta = InterceptorMetadata.with(this,
                resolveTarget(invocation, InvalidatingOperation.class));

        return cacheService.aroundInvalidateMethodInvocation(invocation, meta);
    }
}
