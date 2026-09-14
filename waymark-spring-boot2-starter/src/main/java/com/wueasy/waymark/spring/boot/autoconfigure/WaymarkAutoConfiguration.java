package com.wueasy.waymark.spring.boot.autoconfigure;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.wueasy.waymark.ClientConfig;
import com.wueasy.waymark.WaymarkClient;
import com.wueasy.waymark.model.ConfigItem;
import com.wueasy.waymark.model.InstanceRequest;
import com.wueasy.waymark.quickstart.ConfigWatcher;
import com.wueasy.waymark.quickstart.ConfigWatcherOptions;
import com.wueasy.waymark.quickstart.RegistryResolver;
import com.wueasy.waymark.quickstart.RegistryResolverOptions;

/**
 * Waymark Spring Boot 自动配置。引入 starter 后，仅需在配置文件中填写 {@code waymark} 前缀的配置即可：
 * <ul>
 *   <li>{@code waymark.config.data-ids} 非空时自动创建 {@link ConfigWatcher}，加载配置并监听变更，
 *       配置内容会写入 Spring {@code Environment} 并发布变更事件，实现热加载。</li>
 *   <li>{@code waymark.registry.enabled=true} 时自动创建 {@link RegistryResolver}，完成在线实例缓存与
 *       服务发现；{@code waymark.registry.service-name} 非空时同时自动注册自身实例并保持心跳。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WaymarkProperties.class)
@ConditionalOnProperty(prefix = "waymark", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WaymarkAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaymarkAutoConfiguration.class);

    /**
     * 配置热加载器：将监听到的配置写入 {@code Environment}，并发布 {@code EnvironmentChangeEvent}
     * 触发 {@code @ConfigurationProperties} 重新绑定。
     */
    @Bean
    @ConditionalOnMissingBean
    public WaymarkConfigRefresher waymarkConfigRefresher(ConfigurableApplicationContext applicationContext) {
        return new WaymarkConfigRefresher(applicationContext);
    }

    /** Waymark 客户端，应用可直接注入使用配置中心与注册中心的全部 API。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public WaymarkClient waymarkClient(WaymarkProperties properties) {
        ClientConfig config = ClientConfig.builder()
                .endpoint(properties.getEndpoint())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .token(properties.getToken())
                .timeoutMillis((int) toMillis(properties.getTimeout()))
                .cacheDir(properties.getCacheDir())
                .disableCache(properties.isDisableCache())
                .build();
        return WaymarkClient.create(config);
    }

    /**
     * 配置监听器：启动时加载一次配置并热加载到 {@code Environment}，之后订阅配置变更并实时刷新。
     * 仅在 {@code waymark.config.enabled=true} 且配置了 {@code waymark.config.data-ids} 时创建。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @Conditional(WaymarkConfigWatchCondition.class)
    public ConfigWatcher waymarkConfigWatcher(WaymarkClient client, WaymarkProperties properties,
            final WaymarkConfigRefresher refresher) {
        WaymarkProperties.Config config = properties.getConfig();
        ConfigWatcherOptions options = new ConfigWatcherOptions();
        options.namespace = config.getNamespace();
        options.groupName = config.getGroupName();
        options.dataIds = config.getDataIds();
        options.reconnectDelayMillis = toMillis(config.getReconnectDelay());
        options.onChange = new Consumer<ConfigItem>() {
            @Override
            public void accept(ConfigItem item) {
                refresher.apply(item);
            }
        };
        options.onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                LOGGER.warn("waymark: 配置监听异常: {}", error.getMessage());
            }
        };
        return client.newConfigWatcher(options);
    }

    /**
     * 服务发现解析器：缓存在线实例并提供平滑加权轮询选择；{@code waymark.registry.service-name}
     * 非空时自动注册自身实例并保持心跳。仅在 {@code waymark.registry.enabled=true} 时创建。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "waymark.registry", name = "enabled", havingValue = "true")
    public RegistryResolver waymarkRegistryResolver(WaymarkClient client, WaymarkProperties properties) {
        WaymarkProperties.Registry registry = properties.getRegistry();
        RegistryResolverOptions options = new RegistryResolverOptions();
        options.namespace = registry.getNamespace();
        options.groupName = registry.getGroupName();
        options.heartbeatIntervalMillis = toMillis(registry.getHeartbeatInterval());
        options.refreshIntervalMillis = toMillis(registry.getRefreshInterval());
        options.reconnectDelayMillis = toMillis(registry.getReconnectDelay());
        options.onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                LOGGER.warn("waymark: 服务发现异常: {}", error.getMessage());
            }
        };
        if (notBlank(registry.getServiceName())) {
            options.self = new InstanceRequest()
                    .serviceName(registry.getServiceName())
                    .ip(registry.getIp())
                    .port(registry.getPort())
                    .clusterName(registry.getClusterName())
                    .weight(registry.getWeight())
                    .metadata(registry.getMetadata());
        }
        return client.newRegistryResolver(options);
    }

    private static long toMillis(java.time.Duration duration) {
        return duration == null ? 0L : duration.toMillis();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
