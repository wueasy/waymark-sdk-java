package com.wueasy.waymark;

import org.slf4j.Logger;

/**
 * 客户端配置。采用构建器创建，未显式设置的项使用默认值：
 *
 * <pre>
 * ClientConfig config = ClientConfig.builder()
 *         .endpoint("http://127.0.0.1:9868")
 *         .username("admin")
 *         .password("123456")
 *         .build();
 * </pre>
 *
 * endpoint 支持配置多个服务端地址（英文逗号分隔），例如
 * {@code "http://a:9868,http://b:9868"}：默认使用第一个地址，
 * 当请求遇到网络层错误（连接失败、超时、读写失败等）时，会自动切换到下一个地址重试（故障转移）。
 */
public final class ClientConfig {

    /** 服务端地址，可包含多个以英文逗号分隔的地址。 */
    private String endpoint;

    private String username;

    private String password;

    private String token;

    /** 单次请求超时时间（毫秒），小于等于 0 时使用默认值 10s（订阅长连接不受此限制）。 */
    private int timeoutMillis;

    /** 本地配置缓存目录，为空时使用系统用户缓存目录下的 waymark 目录。 */
    private String cacheDir;

    /** 关闭本地配置缓存；默认开启，配置中心不可用时回退到本地缓存。 */
    private boolean disableCache;

    /** 可选的日志记录器，用于输出调试日志；为空时使用名为 com.wueasy.waymark 的日志记录器。 */
    private Logger logger;

    private ClientConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getToken() {
        return token;
    }

    public int getTimeoutMillis() {
        return timeoutMillis > 0 ? timeoutMillis : WaymarkConstants.DEFAULT_TIMEOUT_MILLIS;
    }

    public String getCacheDir() {
        return cacheDir == null ? "" : cacheDir.trim();
    }

    public boolean isDisableCache() {
        return disableCache;
    }

    public Logger getLogger() {
        return logger;
    }

    /** 客户端配置构建器。 */
    public static final class Builder {

        private final ClientConfig config = new ClientConfig();

        /** 服务端地址，例如 http://127.0.0.1:9868，必填；多个地址使用英文逗号分隔，支持故障转移。 */
        public Builder endpoint(String endpoint) {
            config.endpoint = endpoint;
            return this;
        }

        /** 登录用户名，配置后可在令牌缺失或过期时自动登录。 */
        public Builder username(String username) {
            config.username = username;
            return this;
        }

        /** 登录密码。 */
        public Builder password(String password) {
            config.password = password;
            return this;
        }

        /** 已有的访问令牌，非空时直接使用。 */
        public Builder token(String token) {
            config.token = token;
            return this;
        }

        /** 单次请求超时时间（毫秒）。 */
        public Builder timeoutMillis(int timeoutMillis) {
            config.timeoutMillis = timeoutMillis;
            return this;
        }

        /** 本地配置缓存目录。 */
        public Builder cacheDir(String cacheDir) {
            config.cacheDir = cacheDir;
            return this;
        }

        /** 是否关闭本地配置缓存。 */
        public Builder disableCache(boolean disableCache) {
            config.disableCache = disableCache;
            return this;
        }

        /** 日志记录器。 */
        public Builder logger(Logger logger) {
            config.logger = logger;
            return this;
        }

        public ClientConfig build() {
            return config;
        }
    }
}
