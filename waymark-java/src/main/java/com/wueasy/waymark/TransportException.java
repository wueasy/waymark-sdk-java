package com.wueasy.waymark;

/**
 * 网络传输异常：连接失败、超时、读写失败等。
 * 该异常表示配置中心不可达，配置读取可安全回退到本地缓存。
 */
public class TransportException extends WaymarkException {

    private static final long serialVersionUID = 1L;

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
