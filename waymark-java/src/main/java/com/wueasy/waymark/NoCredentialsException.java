package com.wueasy.waymark;

/**
 * 未配置用户名密码且未提供令牌，无法自动登录。
 * 订阅等长连接场景下该异常表示不可恢复错误，不会触发重连。
 */
public class NoCredentialsException extends WaymarkException {

    private static final long serialVersionUID = 1L;

    public NoCredentialsException() {
        super("waymark: 未配置用户名密码，无法登录");
    }
}
