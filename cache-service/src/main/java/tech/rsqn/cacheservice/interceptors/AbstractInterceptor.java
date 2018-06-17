package tech.rsqn.cacheservice.interceptors;

import tech.rsqn.cacheservice.TransparentCacheService;
import tech.rsqn.cacheservice.annotations.InvalidatingOperation;
import tech.rsqn.cacheservice.annotations.ReadOperation;
import tech.rsqn.cacheservice.annotations.WriteOperation;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.beans.factory.annotation.Required;
import tech.rsqn.useful.things.reflection.ReflectionHelper;

import java.lang.annotation.Annotation;

public abstract class AbstractInterceptor implements MethodInterceptor {
    protected TransparentCacheService cacheService;

    @Required
    public void setCacheService(TransparentCacheService cacheService) {
        this.cacheService = cacheService;
    }

    protected <T extends Annotation> String resolveTarget(
        MethodInvocation invocation, Class<T> expected) {
        Object found = ReflectionHelper.getAnnotationFromInvocation(invocation,
                expected);

        if (found != null) {
            if (found instanceof ReadOperation) {
                ReadOperation readOperation = (ReadOperation) found;

                return readOperation.target();
            } else if (found instanceof WriteOperation) {
                WriteOperation writeOperation = (WriteOperation) found;

                return writeOperation.target();
            } else if (found instanceof InvalidatingOperation) {
                InvalidatingOperation invalidatingOperation = (InvalidatingOperation) found;

                return invalidatingOperation.target();
            }
        }

        return null;
    }
}
