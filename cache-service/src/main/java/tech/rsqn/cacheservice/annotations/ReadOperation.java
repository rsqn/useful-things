package tech.rsqn.cacheservice.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReadOperation {
    static final String DEFAULT_TARGET = "default"
    ;
    String target() default DEFAULT_TARGET;
    Class unlessClass() default Object.class;
}
