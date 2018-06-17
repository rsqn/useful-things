
package tech.rsqn.cacheservice.advisors;

import tech.rsqn.cacheservice.annotations.InvalidatingOperation;

import org.aopalliance.aop.Advice;

import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

public class InvalidatingOperationAdvisor
    extends StaticMethodMatcherPointcutAdvisor {
    public InvalidatingOperationAdvisor(Advice advice) {
        super(advice);
    }

    public boolean matches(Method method, Class aClass) {
        Method originalMethod = ReflectionUtils.findMethod(aClass,
                method.getName(), method.getParameterTypes());

        if (originalMethod.isAnnotationPresent(InvalidatingOperation.class) ||
                method.isAnnotationPresent(InvalidatingOperation.class)) {
            return true;
        }

        return false;
    }
}
