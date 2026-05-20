package com.etactics.proxima.sal.sdk.exception;

public class SdkException extends RuntimeException {

    private final String code;

    public SdkException(String code, String message) {
        super(message);
        this.code = code;
    }

    public SdkException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
