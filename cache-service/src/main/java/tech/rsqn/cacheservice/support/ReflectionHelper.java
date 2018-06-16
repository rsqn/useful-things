
package tech.rsqn.cacheservice.support;

import org.aopalliance.intercept.MethodInvocation;

import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;


public class ReflectionHelper {
    public static boolean isPrimitiveOrStringOrWrapper(Object o) {
        return isPrimitiveOrStringOrWrapperClass(o.getClass());
    }

    public static boolean isPrimitiveOrStringOrWrapperClass(Class<?> clazz) {
        if (isWrapperType(clazz)) {
            return true;
        } else if (clazz.isPrimitive()) {
            return true;
        } else if (clazz.equals(String.class)) {
            return true;
        }

        return false;
    }

    public static <T extends Annotation> T getAnnotationFromInvocation(
        MethodInvocation invocation, Class<T> annotationClass) {
        Method method = invocation.getMethod();

        //This may be useful for CGLib		Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
        Method originalMethod = ReflectionUtils.findMethod(invocation.getThis()
                                                                     .getClass(),
                method.getName(), method.getParameterTypes());

        T ret = method.getAnnotation(annotationClass);

        if (ret != null) {
            return ret;
        }

        return originalMethod.getAnnotation(annotationClass);
    }

    public static boolean isWrapperType(Class<?> clazz) {
        return clazz.equals(Boolean.class) || clazz.equals(Integer.class) ||
        clazz.equals(Character.class) || clazz.equals(Byte.class) ||
        clazz.equals(Short.class) || clazz.equals(Double.class) ||
        clazz.equals(Long.class) || clazz.equals(Float.class);
    }
}
