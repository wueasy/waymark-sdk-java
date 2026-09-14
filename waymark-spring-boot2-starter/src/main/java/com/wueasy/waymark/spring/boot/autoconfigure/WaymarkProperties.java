package com.wueasy.waymark.spring.boot.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Waymark 自动配置项，前缀 {@code waymark}。
 *
 * <pre>
 * waymark:
 *   endpoint: http://127.0.0.1:9868
 *   username: admin
 *   password: admin
 *   config:
 *     data-ids: app.yaml,common.yaml
 *   registry:
 *     enabled: true
 *     service-name: order-service
 *     ip: 127.0.0.1
 *     port: 8080
 * </pre>
 */
@ConfigurationProperties(prefix = "waymark")
public class WaymarkProperties {

    /** 是否启用自动配置。 */
    private boolean enabled = true;

    /** 服务端地址，例如 http://127.0.0.1:9868；多个地址使用英文逗号分隔，支持故障转移。 */
    private String endpoint;

    /** 登录用户名。 */
    private String username;

    /** 登录密码。 */
    private String password;

    /** 已有的访问令牌，非空时直接使用。 */
    private String token;

    /** 单次请求超时时间。 */
    private Duration timeout = Duration.ofSeconds(10);

    /** 本地配置缓存目录，为空时使用系统用户缓存目录下的 waymark 目录。 */
    private String cacheDir;

    /** 是否关闭本地配置缓存。 */
    private boolean disableCache;

    /** 配置监听。 */
    private final Config config = new Config();

    /** 服务发现。 */
    private final Registry registry = new Registry();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public boolean isDisableCache() {
        return disableCache;
    }

    public void setDisableCache(boolean disableCache) {
        this.disableCache = disableCache;
    }

    public Config getConfig() {
        return config;
    }

    public Registry getRegistry() {
        return registry;
    }

    /** 配置监听参数，监听 data-ids 指定的配置并支持热加载。 */
    public static class Config {

        /** 是否启用配置监听。 */
        private boolean enabled = true;

        /** 命名空间，为空时使用默认命名空间。 */
        private String namespace;

        /** 分组，为空时使用默认分组。 */
        private String groupName;

        /** 监听的配置文件名（dataId），多个使用英文逗号分隔，配置后自动开启监听。 */
        private List<String> dataIds = new ArrayList<String>();

        /** 订阅断线重连间隔。 */
        private Duration reconnectDelay = Duration.ofSeconds(3);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public List<String> getDataIds() {
            return dataIds;
        }

        public void setDataIds(List<String> dataIds) {
            this.dataIds = dataIds;
        }

        public Duration getReconnectDelay() {
            return reconnectDelay;
        }

        public void setReconnectDelay(Duration reconnectDelay) {
            this.reconnectDelay = reconnectDelay;
        }
    }

    /** 服务发现参数，service-name 非空时自动注册并保持心跳。 */
    public static class Registry {

        /** 是否启用服务发现。 */
        private boolean enabled;

        /** 命名空间，为空时使用默认命名空间。 */
        private String namespace;

        /** 分组，为空时使用默认分组。 */
        private String groupName;

        /** 自身实例的服务名；为空时仅做发现，不注册自身实例。 */
        private String serviceName;

        /** 自身实例的 IP。 */
        private String ip;

        /** 自身实例的端口。 */
        private int port;

        /** 集群名，为空时使用默认集群。 */
        private String clusterName;

        /** 自身实例权重，小于等于 0 时服务端按 1 处理。 */
        private double weight;

        /** 自身实例心跳间隔。 */
        private Duration heartbeatInterval = Duration.ofSeconds(5);

        /** 内存实例缓存的全量刷新间隔。 */
        private Duration refreshInterval = Duration.ofSeconds(30);

        /** 订阅断线重连间隔。 */
        private Duration reconnectDelay = Duration.ofSeconds(3);

        /** 自身实例元数据。 */
        private Map<String, String> metadata = new LinkedHashMap<String, String>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getClusterName() {
            return clusterName;
        }

        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }

        public double getWeight() {
            return weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public Duration getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }

        public Duration getReconnectDelay() {
            return reconnectDelay;
        }

        public void setReconnectDelay(Duration reconnectDelay) {
            this.reconnectDelay = reconnectDelay;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}
