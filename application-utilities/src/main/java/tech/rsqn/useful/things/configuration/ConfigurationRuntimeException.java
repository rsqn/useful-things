package tech.rsqn.useful.things.configuration;

/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: Nov 1, 2012
 * Time: 1:53:06 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConfigurationRuntimeException extends RuntimeException {
    public ConfigurationRuntimeException() {
        super();    //To change body of overridden methods use File | Settings | File Templates.
    }

    public ConfigurationRuntimeException(String s) {
        super(s);    //To change body of overridden methods use File | Settings | File Templates.
    }

    public ConfigurationRuntimeException(String s, Throwable throwable) {
        super(s, throwable);    //To change body of overridden methods use File | Settings | File Templates.
    }

    public ConfigurationRuntimeException(Throwable throwable) {
        super(throwable);    //To change body of overridden methods use File | Settings | File Templates.
    }
}
