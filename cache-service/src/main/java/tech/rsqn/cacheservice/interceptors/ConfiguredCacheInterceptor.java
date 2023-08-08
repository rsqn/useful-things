package tech.rsqn.cacheservice.interceptors;

import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.rsqn.cacheservice.proxy.CachingProxyConfigEntry;

import java.util.ArrayList;
import java.util.List;


public class ConfiguredCacheInterceptor extends AbstractInterceptor {
    private Logger LOG = LoggerFactory.getLogger(getClass());
    private List<CachingProxyConfigEntry> config;

    public void setConfiguration(List<String> configStrings) {
        LOG.info("CachingProxy factory created");

        this.config = new ArrayList<CachingProxyConfigEntry>();

        for (String s : configStrings) {
            CachingProxyConfigEntry entry = new CachingProxyConfigEntry();
            entry.parseFromString(s);
            config.add(entry);
            LOG.info(s);
        }
    }

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
