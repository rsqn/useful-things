/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.interceptors;

import tech.rsqn.cacheservice.annotations.InvalidatingOperation;

import org.aopalliance.intercept.MethodInvocation;


/**
 * Author: mandrewes
 * Date: 17/06/11
 */
public class InvalidatingInterceptor extends AbstractInterceptor {
    public Object invoke(MethodInvocation invocation) throws Throwable {
        InterceptorMetadata meta = InterceptorMetadata.with(this,
                resolveTarget(invocation, InvalidatingOperation.class));

        return cacheService.aroundInvalidateMethodInvocation(invocation, meta);
    }
}
