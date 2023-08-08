package tech.rsqn.useful.things.lambda.exceptions;

public class ErrorCode extends RuntimeException {
    private int code = -1;
    private String message = "";

    public ErrorCode(int code) {
        super();
        this.code = code;
    }

    public ErrorCode(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public ErrorCode(String message) {
        super(message);
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
