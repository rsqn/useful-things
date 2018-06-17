package tech.rsqn.cacheservice.advisors;

import tech.rsqn.cacheservice.annotations.WriteOperation;

import org.aopalliance.aop.Advice;

import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;


public class WriteOperationAdvisor extends StaticMethodMatcherPointcutAdvisor {
    public WriteOperationAdvisor(Advice advice) {
        super(advice);
    }

    public boolean matches(Method method, Class aClass) {
        Method originalMethod = ReflectionUtils.findMethod(aClass,
                method.getName(), method.getParameterTypes());

        if (originalMethod.isAnnotationPresent(WriteOperation.class) ||
                method.isAnnotationPresent(WriteOperation.class)) {
            return true;
        }

        return false;
    }
}
