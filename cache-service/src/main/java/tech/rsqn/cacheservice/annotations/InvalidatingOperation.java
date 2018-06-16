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
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InvalidatingOperation {
    static final String DEFAULT_TARGET = "default"
    ;
    String target() default DEFAULT_TARGET;
}
