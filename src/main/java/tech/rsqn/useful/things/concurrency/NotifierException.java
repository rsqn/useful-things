package tech.rsqn.useful.things.concurrency;

public class NotifierException extends RuntimeException {

    public NotifierException() {
        super();
    }

    public NotifierException(String message) {
        super(message);
    }

    public NotifierException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotifierException(Throwable cause) {
        super(cause);
    }

    protected NotifierException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
