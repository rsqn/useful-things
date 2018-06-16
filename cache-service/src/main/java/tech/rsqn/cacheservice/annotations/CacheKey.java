package tech.rsqn.cacheservice.annotations;


/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 19/01/12
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheKey {
    String template() default "";
    Class generator() default Object.class;
}
