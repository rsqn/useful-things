/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Author: mandrewes
 * Date: 17/06/11
 *
 * <p/>
 * method level annotation for intercepting read operations in order to cache the results
 *
 * @author mandrewes
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReadOperation {
    static final String DEFAULT_TARGET = "default"
    ;
    String target() default DEFAULT_TARGET;
    Class unlessClass() default Object.class;
}
