
package tech.rsqn.cacheservice.proxy;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CachingProxyInterceptor implements MethodInterceptor {
    private Logger LOG = LoggerFactory.getLogger(getClass());

    public Object invoke(MethodInvocation invocation) throws Throwable {
        LOG.info("Caching proxy intercepted [" + invocation + "]");

        Object rval = invocation.proceed();

        return rval;
    }
}
