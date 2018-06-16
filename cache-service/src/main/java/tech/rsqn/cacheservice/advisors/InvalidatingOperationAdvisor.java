/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.advisors;

import tech.rsqn.cacheservice.annotations.InvalidatingOperation;

import org.aopalliance.aop.Advice;

import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;


/**
 * Author: mandrewes
 * Date: 17/06/11
 *
 * <p/>
 * Advisor for invalidating operations
 *
 * @author mandrewes
 */
public class InvalidatingOperationAdvisor
    extends StaticMethodMatcherPointcutAdvisor {
    public InvalidatingOperationAdvisor(Advice advice) {
        super(advice);
    }

    /**
     * This needs to do the lookup below to handle proxied classes
     *
     * @param method
     * @param aClass
     * @return
     */
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
