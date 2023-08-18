package tech.rsqn.useful.things.lambda.model;

import tech.rsqn.useful.things.lambda.exceptions.ErrorCode;

public class ErrorDto {
    private String code;
    private String message;

    public ErrorDto from(ErrorCode src) {
        this.code = "" + src.getCode();
        this.message = src.getMessage();
        return this;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
