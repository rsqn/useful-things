package tech.rsqn.cacheservice.exceptions;


/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 5/10/11
 *
 *
 */
public class CacheReflectionRuntimeException extends RuntimeException {
    public CacheReflectionRuntimeException() {
        super();
    }

    public CacheReflectionRuntimeException(String message) {
        super(message);
    }

    public CacheReflectionRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public CacheReflectionRuntimeException(Throwable cause) {
        super(cause);
    }
}
