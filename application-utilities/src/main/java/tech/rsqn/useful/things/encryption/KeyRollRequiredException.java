package tech.rsqn.useful.things.encryption;

public class KeyRollRequiredException extends RuntimeException {

    public KeyRollRequiredException() {
        super();
    }

    public KeyRollRequiredException(String message) {
        super(message);
    }

    public KeyRollRequiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeyRollRequiredException(Throwable cause) {
        super(cause);
    }

    protected KeyRollRequiredException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
