/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.interceptors;

import tech.rsqn.cacheservice.annotations.WriteOperation;

import org.aopalliance.intercept.MethodInvocation;


/**
 * Author: mandrewes
 * Date: 17/06/11
 *
 * <p/>
 *
 * @author mandrewes
 */
public class WriteInterceptor extends AbstractInterceptor {
    public Object invoke(MethodInvocation invocation) throws Throwable {
        InterceptorMetadata meta = InterceptorMetadata.with(this,
                resolveTarget(invocation, WriteOperation.class));

        return cacheService.aroundWriteMethodInvocation(invocation, meta);
    }
}
