package tech.rsqn.cacheservice.advisors;

import org.aopalliance.aop.Advice;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.util.ReflectionUtils;
import tech.rsqn.cacheservice.annotations.ReadOperation;

import java.lang.reflect.Method;

public class ReadOperationAdvisor extends StaticMethodMatcherPointcutAdvisor {
    public ReadOperationAdvisor(Advice advice) {
        super(advice);
    }

    public boolean matches(Method method, Class aClass) {
        Method originalMethod = ReflectionUtils.findMethod(aClass,
                method.getName(), method.getParameterTypes());

        if (originalMethod.isAnnotationPresent(ReadOperation.class) ||
                method.isAnnotationPresent(ReadOperation.class)) {
            return true;
        }

        return false;
    }
}
