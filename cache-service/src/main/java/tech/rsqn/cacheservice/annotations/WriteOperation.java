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
 * <p/>
 * method level annotation for intercepting write operations in order to invalidate the cache
 *
 * @author mandrewes
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WriteOperation {
    static final String DEFAULT_TARGET = "default"
    ;
    String target() default DEFAULT_TARGET;
}
