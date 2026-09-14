package com.wueasy.waymark.model;

/**
 * 登录结果。
 */
public class LoginResult {

    /** 访问令牌，同时会被缓存到客户端。 */
    public String token;

    /** 令牌有效期（秒）。 */
    public long expiresIn;

    /** 登录用户资料。 */
    public UserProfile user;
}
