package tech.rsqn.cacheservice.referencetransparentcache;

import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ReflectionUtils;
import tech.rsqn.cacheservice.CacheService;
import tech.rsqn.cacheservice.TransparentCacheService;
import tech.rsqn.cacheservice.annotations.CacheKey;
import tech.rsqn.cacheservice.annotations.ReadOperation;
import tech.rsqn.cacheservice.exceptions.CacheReflectionRuntimeException;
import tech.rsqn.cacheservice.interceptors.InterceptorMetadata;
import tech.rsqn.cacheservice.interceptors.InterceptorUtil;
import tech.rsqn.cacheservice.support.*;
import tech.rsqn.cacheservice.util.GroupTimer;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * Author: mandrewes
 * Date: 16/06/11
 *
 * @author mandrewes
 */
public class DefaultTransparentCacheService implements TransparentCacheService {
    private boolean debugLogging = false;
    private Logger log = LoggerFactory.getLogger(getClass());
    private String defaultCacheName = "default";
    private Map<String, CacheService> caches;
    private List<CacheKeyGenerator> keyGenerators;
    private List<ParameterKeyGenerator> parameterKeyGenerators;
    private boolean cachingDisabled = false;
    private ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
    private ExpressionParser expressionParser = new SpelExpressionParser();
    private boolean supportParameterNameDiscovery = false;
    private GroupTimer groupTimer;

    public DefaultTransparentCacheService() {
        groupTimer = new GroupTimer();
    }

    @Required
    public void setCaches(
        Map<String, CacheService> caches) {
        this.caches = caches;
    }

    public Map<String, CacheService> getCaches() {
        return caches;
    }

    public void setSupportParameterNameDiscovery(
        boolean supportParameterNameDiscovery) {
        this.supportParameterNameDiscovery = supportParameterNameDiscovery;
    }

    /**
     * For testing and debugging
     */
    public void disableCaching() {
        this.cachingDisabled = true;
    }

    /**
     * For testing and debugging
     */
    public void enableCaching() {
        this.cachingDisabled = true;
    }

    @Required
    public void setDefaultCacheName(String defaultCacheName) {
        this.defaultCacheName = defaultCacheName;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    @Required
    public void setKeyGenerators(List<CacheKeyGenerator> keyGenerators) {
        this.keyGenerators = keyGenerators;
    }

    @Required
    public void setParameterKeyGenerators(
        List<ParameterKeyGenerator> parameterKeyGenerators) {
        this.parameterKeyGenerators = parameterKeyGenerators;
    }

    public long count() {
        long ret = 0;

        for (CacheService cache : caches.values()) {
            ret += cache.count();
        }

        return ret;
    }

    public long clear() {
        long ret = 0;

        for (CacheService cache : caches.values()) {
            ret += cache.clear();
        }

        return ret;
    }

    /**
     * @return
     * @inheritDoc
     */
    public List<Class> getSupportedTypes() {
        List<Class> ret = new ArrayList<Class>();

        for (CacheKeyGenerator keyGenerator : keyGenerators) {
            ret.addAll(keyGenerator.getSupportedClasses());
        }

        return ret;
    }

    /**
     * Generates a key for a parameter
     *
     * @param parameter
     * @return
     */
    public String generateParameterKey(Object parameter) {
        String key = null;

        for (ParameterKeyGenerator generator : parameterKeyGenerators) {
            if (parameter != null) {
                if (generator.supportsClass(parameter.getClass())) {
                    key = generator.generateParamKey(parameter);

                    if (key != null) {
                        return key;
                    }
                }
            }
        }

        return null;
    }

    /**
     * This implementation is inefficent however allows for flexibility
     * until the entities are better known
     * <p/>
     * It may be preferable to have a key generator that can generate keys for multiple entities
     *
     * @param clazz
     * @param arguments
     * @return
     * @inheritDoc
     */
    public String generateCacheKey(Class clazz, Object... arguments) {
        String key = null;

        for (CacheKeyGenerator keyGenerator : keyGenerators) {
            if (keyGenerator.supportsClass(clazz)) {
                key = keyGenerator.generateKey(this, clazz, arguments);

                if (key != null) {
                    return key;
                }
            }
        }

        return null;
    }

    private String generateKeyFromCacheKeyAnnotation(
        MethodInvocation invocation, CacheKey cacheKeyAnnotation) {
        String cacheKey;

        if (cacheKeyAnnotation.generator() != Object.class) {
            try {
                CacheKeyGenerator generator = (CacheKeyGenerator) cacheKeyAnnotation.generator()
                                                                                    .newInstance();

                if ((invocation.getArguments() != null) &&
                        (invocation.getArguments().length > 0)) {
                    for (Object o : invocation.getArguments()) {
                        if ((o != null) && generator.supportsEntity(o)) {
                            cacheKey = generator.generateKey(o);

                            if (cacheKey != null) {
                                return cacheKey;
                            }
                        }
                    }
                }
            } catch (InstantiationException e) {
                throw new CacheReflectionRuntimeException(
                    "Exception using Cache Key Generator " +
                    cacheKeyAnnotation.generator(), e);
            } catch (IllegalAccessException e) {
                throw new CacheReflectionRuntimeException(
                    "Exception using Cache Key Generator " +
                    cacheKeyAnnotation.generator(), e);
            }
        } else if ((cacheKeyAnnotation.template() != null) &&
                (cacheKeyAnnotation.template().length() > 0)) {
            return generateCacheKeyFromTemplate(invocation,
                cacheKeyAnnotation.template());
        }

        return null;
    }

    public String generateCacheKeyFromTemplate(MethodInvocation mi,
        String keyTemplate) {
        Method method = ReflectionUtils.findMethod(mi.getThis().getClass(),
                mi.getMethod().getName(), mi.getMethod().getParameterTypes());
        EvaluationContext context = new StandardEvaluationContext();

        final Object[] arguments = mi.getArguments();
        final String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);

        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], arguments[i]);
            }
        }

        for (int i = 0; i < arguments.length; i++) {
            context.setVariable("arg" + i, arguments[i]);
        }

        final Expression expression = expressionParser.parseExpression(keyTemplate,
                new TemplatedParserContext());

        return expression.getValue(context, String.class);
    }

    /**
     * This implementation is inefficent however allows for flexibility
     * until the entities are better known
     * <p/>
     * It may be preferable to have a key generator that can generate keys for multiple entities
     *
     * @param entity
     * @param <T>
     * @return
     * @inheritDoc
     */
    public <T extends Serializable> String generateCacheKey(T entity) {
        String key = null;

        for (CacheKeyGenerator keyGenerator : keyGenerators) {
            if (keyGenerator.supportsEntity(entity)) {
                key = keyGenerator.generateKey(entity);

                if (key != null) {
                    return key;
                }
            }
        }

        return null;
    }

    /**
     * @param invocation
     * @return
     * @throws Throwable
     */
    public Object aroundWriteMethodInvocation(
        final MethodInvocation invocation, InterceptorMetadata meta)
        throws Throwable {
        groupTimer.start("aroundWriteMethodInvocation");

        Object[] args = invocation.getArguments();
        final Method targetMethod = invocation.getMethod();
        Class<?> returnType = targetMethod.getReturnType();
        final CacheService cache = resolveCacheService(meta);

        if (debugLogging) {
            log.debug(MessageFormat.format(
                    "Write Interceptor called for {0}.{1} with return type {2} and {3} arguments",
                    targetMethod.getDeclaringClass(), targetMethod.getName(),
                    returnType.getName(), args.length));
        }

        CacheKey cacheKeyAnnotation = ReflectionHelper.getAnnotationFromInvocation(invocation,
                CacheKey.class);

        String cacheKey = null;

        if (cacheKeyAnnotation != null) {
            cacheKey = generateKeyFromCacheKeyAnnotation(invocation,
                    cacheKeyAnnotation);
        }

        if (cacheKey != null) {
            log.debug("Write Interceptor invalidating key (" + cacheKey + ")");
            cache.remove(cacheKey);
        }

        /*
        GraphInspectorTypeMatcher matcher = new GraphInspectorTypeMatcher().withClasses(getSupportedTypes());

        GraphInspector inspector = new GraphInspector().withObject(args)
                                                       .withMatcher(matcher).withCallBack(new GraphInspectorCallBack<Object>() {
                    public boolean onCallBack(Object o) {
                        if (!(o instanceof Serializable)) {
                            return true;
                        }

                        String cacheKey = generateCacheKey((Serializable) o);

                        int n = 0;

                        if (cacheKey != null) {
                            if (debugLogging) {
                                log.debug(MessageFormat.format(
                                        "Write Interceptor cache key {0} ",
                                        cacheKey));
                            }

                            n = cache.remove(cacheKey);
                        }

                        if (debugLogging) {
                            if (n > 0) {
                                log.debug(MessageFormat.format(
                                        "Write Interceptor removed {0} items from cache",
                                        n));
                            }
                        }

                        return true;
                    }
                });

        inspector.runInspection();
        */

        groupTimer.stopAndReport("aroundWriteMethodInvocation");

        Object returnValue = invocation.proceed();

        return returnValue;
    }

    /**
     * @param invocation
     * @return
     * @throws Throwable
     */
    public Object aroundReadMethodInvocation(MethodInvocation invocation,
        InterceptorMetadata meta) throws Throwable {
        groupTimer.start("aroundReadMethodInvocation-READ");

        Object[] args = invocation.getArguments();
        Method targetMethod = invocation.getMethod();
        Class<?> returnType = targetMethod.getReturnType();
        CacheService cache = resolveCacheService(meta);

        if (cachingDisabled) {
            log.debug("caching is disabled - executing method ");
            groupTimer.stopAndReport("aroundReadMethodInvocation-READ");

            return invocation.proceed();
        }

        if (debugLogging) {
            log.debug(MessageFormat.format(
                    "Read Interceptor called for {0}.{1} with return type {2} and {3} arguments",
                    targetMethod.getDeclaringClass().getName(),
                    targetMethod.getName(), returnType.getName(), args.length));
        }

        CacheKey cacheKeyAnnotation = ReflectionHelper.getAnnotationFromInvocation(invocation,
                CacheKey.class);
        ReadOperation readOperation = ReflectionHelper.getAnnotationFromInvocation(invocation,
                ReadOperation.class);

        String entityCacheKey = null;
        String methodCacheKey = null;

        if ((cacheKeyAnnotation != null) && (args.length > 0)) {
            entityCacheKey = generateKeyFromCacheKeyAnnotation(invocation,
                    cacheKeyAnnotation);
        }  else {
            entityCacheKey = generateCacheKey(returnType, args);
        }

        if (entityCacheKey == null) {
            methodCacheKey = InterceptorUtil.generateCacheKeyBasedOnMethodInvocation(this,
                    invocation);
        }

        if (debugLogging) {
            log.debug(MessageFormat.format(
                    "Read Interceptor entityCacheKey ({0}) , methodCacheKey ({1}) ",
                    entityCacheKey, methodCacheKey));
        }

        if (entityCacheKey != null) {
            Object cachedValue = null;

            if (cache.containsKey(entityCacheKey)) {
                cachedValue = cache.get(entityCacheKey);
            }

            if (cachedValue != null) {
                if (debugLogging) {
                    log.debug("Read Interceptor will return item from cache  (" +
                        entityCacheKey + ")");
                }

                if (TransparentCacheExecutionBehaviour.getBehaviour()
                                                          .isClearCacheAfterRead()) {
                    log.debug("Read Interceptor Clearing cache entity key  (" +
                        entityCacheKey + ") based on " +
                        TransparentCacheExecutionBehaviour.getBehaviour());
                    cache.remove(entityCacheKey);
                }

                if (cachedValue instanceof NonSerializableWrapper) {
                    NonSerializableWrapper wrapper = (NonSerializableWrapper) cachedValue;

                    if (wrapper.getValue() != null) {
                        groupTimer.stopAndReport(
                            "aroundReadMethodInvocation-READ");

                        return wrapper.getValue();
                    }
                } else {
                    groupTimer.stopAndReport("aroundReadMethodInvocation-READ");

                    return cachedValue;
                }
            }

            if (debugLogging) {
                log.debug("Read Interceptor not found in cache (" +
                    entityCacheKey + ")");
            }
        }

        if ((methodCacheKey != null) && (entityCacheKey == null)) {
            Object cachedValue = null;

            if (cache.containsKey(methodCacheKey)) {
                cachedValue = cache.get(methodCacheKey);
            }

            if (cachedValue != null) {
                if (debugLogging) {
                    log.debug("Read Interceptor returning item from cache (" +
                        methodCacheKey + ")");
                }

                if (TransparentCacheExecutionBehaviour.getBehaviour()
                                                          .isClearCacheAfterRead()) {
                    log.debug("Read Interceptor Clearing cache method key  (" +
                        methodCacheKey + ") based on " +
                        TransparentCacheExecutionBehaviour.getBehaviour());
                    cache.remove(methodCacheKey);
                }

                if (cachedValue instanceof NonSerializableWrapper) {
                    NonSerializableWrapper wrapper = (NonSerializableWrapper) cachedValue;

                    if (wrapper.getValue() != null) {
                        groupTimer.stopAndReport(
                            "aroundReadMethodInvocation-READ");

                        return wrapper.getValue();
                    }
                } else {
                    groupTimer.stopAndReport("aroundReadMethodInvocation-READ");

                    return cachedValue;
                }
            }

            if (debugLogging) {
                log.debug("Read Interceptor not found in cache (" +
                    methodCacheKey + ")");
            }
        }

        if (TransparentCacheExecutionBehaviour.getBehaviour()
                                                  .isReturnIfItemIsNotCached()) {
            log.debug("Read Interceptor NOT proceeding with invocation (" +
                entityCacheKey + ") (" + methodCacheKey + ") based on " +
                TransparentCacheExecutionBehaviour.getBehaviour());
            groupTimer.stopAndReport("aroundReadMethodInvocation-READ");

            return null;
        }

        if (debugLogging) {
            log.debug("Read Interceptor proceeding with invocation (" +
                entityCacheKey + ") (" + methodCacheKey + ")");
        }

        groupTimer.stopAndReport("aroundReadMethodInvocation-READ");

        Object returnValue = invocation.proceed();
        Serializable cacheValue = null;

        groupTimer.start("aroundReadMethodInvocation-WRITE");

        if ((readOperation != null) &&
                (readOperation.unlessClass() != Object.class) &&
                (returnValue != null)) {
            ReadOperationUnlessKludge kludge = (ReadOperationUnlessKludge) readOperation.unlessClass()
                                                                                        .newInstance();

            if (!kludge.allowCaching(returnValue)) {
                log.info("ReadOperationUnlessKludge did not allow caching of(" +
                    entityCacheKey + ") (" + methodCacheKey + ")");
                groupTimer.stopAndReport("aroundReadMethodInvocation-WRITE");

                return returnValue;
            }
        }

        if ((returnValue != null) && !(returnValue instanceof Serializable)) {
            if (debugLogging) {
                log.debug(
                    "Return value is not serializable, wrapping in NonSerializableWrapper to cache in memory only (" +
                    entityCacheKey + ") (" + methodCacheKey + ")");
            }

            cacheValue = NonSerializableWrapper.with(returnValue);
        } else if (returnValue instanceof Serializable) {
            cacheValue = (Serializable) returnValue;
        }

        if ((cacheValue != null) && (entityCacheKey != null)) {
            cache.put(entityCacheKey, cacheValue);

            if (debugLogging) {
                log.debug("Read Interceptor cached single entity (" +
                    entityCacheKey + ")");
            }
        }

        /** End of support for single entities and begin of support for collections, maps and arrays **/
        if ((methodCacheKey != null) && (entityCacheKey == null)) {
            if (returnValue instanceof Collection) {
                if (debugLogging) {
                    log.debug("Read Interceptor cached collection under key (" +
                        methodCacheKey + ")");
                }

                cache.put(methodCacheKey, cacheValue);
            } else if (returnValue instanceof Map) {
                if (debugLogging) {
                    log.debug("Read Interceptor cached map under key (" +
                        methodCacheKey + ")");
                }

                cache.put(methodCacheKey, cacheValue);
            } else if (returnValue instanceof Object[]) {
                if (debugLogging) {
                    log.debug("Read Interceptor cached array under key (" +
                        methodCacheKey + ")");
                }

                cache.put(methodCacheKey, cacheValue);
            } else {
                if (debugLogging) {
                    log.debug(
                        "Read Interceptor cached single object under method key (" +
                        methodCacheKey + ")");
                }

                cache.put(methodCacheKey, cacheValue);
            }
        }

        groupTimer.stopAndReport("aroundReadMethodInvocation-WRITE");

        return returnValue;
    }

    /**
     * @param invocation
     * @return
     * @throws Throwable
     */
    public Object aroundInvalidateMethodInvocation(
        MethodInvocation invocation, InterceptorMetadata meta)
        throws Throwable {
        groupTimer.start("aroundInvalidateMethodInvocation");

        Object[] args = invocation.getArguments();
        Method targetMethod = invocation.getMethod();
        Class<?> returnType = targetMethod.getReturnType();
        CacheService cache = resolveCacheService(meta);

        if (debugLogging) {
            log.debug(MessageFormat.format(
                    "Invalidating Interceptor called for {0}.{1} with return type {2} and {3} arguments",
                    targetMethod.getDeclaringClass(), targetMethod.getName(),
                    returnType.getName(), args.length));
        }

        CacheKey cacheKeyAnnotation = ReflectionHelper.getAnnotationFromInvocation(invocation,
                CacheKey.class);

        int nRemoved = 0;
        String cacheKey = null;

        if (cacheKeyAnnotation != null) {
            cacheKey = generateKeyFromCacheKeyAnnotation(invocation,
                    cacheKeyAnnotation);
        }

        if (cacheKey != null) {
            log.debug("Invalidating Interceptor invalidating key (" + cacheKey +
                ")");
            cache.remove(cacheKey);
            nRemoved++;
        }

        if (cacheKeyAnnotation == null) {
            for (Object arg : args) {
                if (arg instanceof Serializable) {
                    String cacheKeyFromArgs = generateCacheKey((Serializable) arg);

                    if (cacheKeyFromArgs != null) {
                        log.debug("Invalidating Interceptor invalidating key (" +
                            cacheKeyFromArgs + ")");
                        cache.remove(cacheKeyFromArgs);
                        nRemoved++;
                    }
                }
            }
        }

        String methodCacheKey = null;

        if (nRemoved == 0) {
            methodCacheKey = InterceptorUtil.generateCacheKeyBasedOnMethodInvocation(this,
                    invocation);
        }

        if (methodCacheKey != null) {
            log.debug("Invalidating Interceptor invalidating key (" +
                methodCacheKey + ")");
            cache.remove(methodCacheKey);
        }

        groupTimer.stopAndReport("aroundInvalidateMethodInvocation");

        Object returnValue = invocation.proceed();

        return returnValue;
    }

    private CacheService resolveCacheService(InterceptorMetadata meta) {
        if (meta.getTarget() != null) {
            return caches.get(meta.getTarget());
        }

        return caches.get(defaultCacheName);
    }
}