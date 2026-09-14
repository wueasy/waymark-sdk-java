package com.wueasy.waymark;

/**
 * Waymark SDK 基础异常，所有 SDK 异常均继承自该类。
 * 为非受检异常，避免对调用方造成过多的 try/catch 负担。
 */
public class WaymarkException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WaymarkException(String message) {
        super(message);
    }

    public WaymarkException(String message, Throwable cause) {
        super(message, cause);
    }
}
