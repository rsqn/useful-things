/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.interceptors;

import tech.rsqn.cacheservice.proxy.CachingProxyConfigEntry;

import org.aopalliance.intercept.MethodInvocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.List;


/**
 * Author: mandrewes
 * Date: 17/06/11
 *
 *
 * Simple caching interceptor driven from configuration. Note that overloaded methods are not supported.
 *
 * @author mandrewes
 */
public class ConfiguredCacheInterceptor extends AbstractInterceptor {
    private Logger log = LoggerFactory.getLogger(getClass());
    private List<CachingProxyConfigEntry> config;

    @Required
    public void setConfiguration(List<String> configStrings) {
        log.info("CachingProxy factory created");

        this.config = new ArrayList<CachingProxyConfigEntry>();

        for (String s : configStrings) {
            CachingProxyConfigEntry entry = new CachingProxyConfigEntry();
            entry.parseFromString(s);
            config.add(entry);
            log.info(s);
        }
    }

    /**
     * iterates through configuration to find a match. Probably the slowest method of intercepting for the cache, but sometimes
     * unavoidable due to generated client jars.
     * @param invocation
     * @return
     * @throws Throwable
     */
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String methodName = invocation.getMethod().getName();

        for (CachingProxyConfigEntry configEntry : config) {
            if (methodName.equals(configEntry.getMethodName())) {
                InterceptorMetadata meta = InterceptorMetadata.with(configEntry.getOperation(),
                        configEntry.getTarget());

                if (InterceptorMetadata.Operation.Read.equals(
                            configEntry.getOperation())) {
                    return cacheService.aroundReadMethodInvocation(invocation,
                        meta);
                } else if (InterceptorMetadata.Operation.Write.equals(
                            configEntry.getOperation())) {
                    return cacheService.aroundWriteMethodInvocation(invocation,
                        meta);
                } else if (InterceptorMetadata.Operation.Invalidate.equals(
                            configEntry.getOperation())) {
                    return cacheService.aroundInvalidateMethodInvocation(invocation,
                        meta);
                }
            }
        }

        return invocation.proceed();
    }
}
