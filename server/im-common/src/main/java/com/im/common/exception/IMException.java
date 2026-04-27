package com.im.common.exception;

public class IMException extends RuntimeException {

    private final int code;

    public IMException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
