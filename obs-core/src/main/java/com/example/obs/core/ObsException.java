package com.example.obs.core;

/**
 * 对象存储层异常。
 */
public class ObsException extends RuntimeException {

    public ObsException(String message) {
        super(message);
    }

    public ObsException(String message, Throwable cause) {
        super(message, cause);
    }
}
