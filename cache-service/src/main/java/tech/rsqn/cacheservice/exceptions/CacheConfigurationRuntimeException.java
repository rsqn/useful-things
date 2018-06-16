/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.exceptions;


/**
 * Author: mandrewes
 * Date: 15/08/11
 */
public class CacheConfigurationRuntimeException extends RuntimeException {
    public CacheConfigurationRuntimeException() {
        super(); //To change body of overridden methods use File | Settings | File Templates.
    }

    public CacheConfigurationRuntimeException(String message) {
        super(message); //To change body of overridden methods use File | Settings | File Templates.
    }

    public CacheConfigurationRuntimeException(String message, Throwable cause) {
        super(message, cause); //To change body of overridden methods use File | Settings | File Templates.
    }

    public CacheConfigurationRuntimeException(Throwable cause) {
        super(cause); //To change body of overridden methods use File | Settings | File Templates.
    }
}
