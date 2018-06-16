/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.proxy;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Author: mandrewes
 * Date: 12/08/11
 *
 *
 * Interceptor for explicitly configured spring proxies
 *
 * @author mandrewes
 */
public class CachingProxyInterceptor implements MethodInterceptor {
    private Logger log = LoggerFactory.getLogger(getClass());

    public Object invoke(MethodInvocation invocation) throws Throwable {
        log.info("Caching proxy intercepted [" + invocation + "]");

        Object rval = invocation.proceed();

        return rval;
    }
}
