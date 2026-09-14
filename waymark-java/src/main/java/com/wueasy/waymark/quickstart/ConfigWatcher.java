package com.wueasy.waymark.quickstart;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.wueasy.waymark.WaymarkConstants;
import com.wueasy.waymark.WaymarkException;
import com.wueasy.waymark.internal.cache.ConfigCache;
import com.wueasy.waymark.internal.config.ConfigApi;
import com.wueasy.waymark.internal.subscribe.SubscriptionHub;
import com.wueasy.waymark.internal.transport.TransportClient;
import com.wueasy.waymark.internal.util.Strings;
import com.wueasy.waymark.model.ConfigItem;
import com.wueasy.waymark.model.Event;

/**
 * 配置监听器：创建时加载一次当前配置并回调，随后在后台订阅变更，配置更新时自动拉取最新内容
 * 并回调。使用 {@link #close()} 释放。
 */
public final class ConfigWatcher implements Closeable {

    private final TransportClient client;

    private final String namespace;

    private final String group;

    private final List<String> dataIds;

    private final Consumer<ConfigItem> onChange;

    private final Consumer<Throwable> onError;

    private final Map<String, ConfigItem> configs = new ConcurrentHashMap<String, ConfigItem>();

    private final SubscriptionHub.Lease subscription;

    private final Object closeLock = new Object();

    private boolean closed;

    private ConfigWatcher(TransportClient client, ConfigWatcherOptions options, List<String> dataIds) {
        this.client = client;
        this.namespace = ConfigCache.normalizeKey(options.namespace, WaymarkConstants.DEFAULT_NAMESPACE);
        this.group = ConfigCache.normalizeKey(options.groupName, WaymarkConstants.DEFAULT_GROUP);
        this.dataIds = dataIds;
        this.onChange = options.onChange;
        this.onError = options.onError;
        client.logger().info("waymark: 启动配置监听 namespace={} group={} dataIds={}", namespace, group, dataIds);

        // 初始化：加载一次当前配置并回调，保证调用方在启动阶段即可拿到配置。
        for (String dataId : dataIds) {
            ConfigItem item;
            try {
                item = ConfigApi.getConfig(client, namespace, group, dataId);
            } catch (RuntimeException e) {
                client.logger().error("waymark: 加载配置 {} 失败: {}", dataId, e.getMessage());
                throw new WaymarkException("waymark: 加载配置 " + dataId + " 失败: " + e.getMessage(), e);
            }
            store(dataId, item);
            client.logger().debug("waymark: 配置 {} 已加载", dataId);
            onChange.accept(item);
        }

        // 与同分组的实例订阅复用同一条 SSE 连接，仅订阅这些 dataId 的配置变更。
        this.subscription = client.sharedSubscriptions().subscribe(namespace, group,
                options.reconnectDelayMillis, dataIds, new Consumer<Event>() {
                    @Override
                    public void accept(Event event) {
                        handleEvent(event);
                    }
                });
    }

    /**
     * 创建配置监听器：先为每个 dataId 加载一次当前配置并回调，随后在后台订阅配置变更，
     * 变更时自动拉取最新配置并回调。
     */
    public static ConfigWatcher create(TransportClient client, ConfigWatcherOptions options) {
        if (options.onChange == null) {
            throw new WaymarkException("waymark: OnChange 回调不能为空");
        }
        List<String> dataIds = normalizeValues(options.dataIds);
        if (dataIds.isEmpty()) {
            throw new WaymarkException("waymark: 至少需要指定一个 dataId");
        }
        return new ConfigWatcher(client, options, dataIds);
    }

    /** 返回最近一次加载到的配置，未命中时返回 null。 */
    public ConfigItem get(String dataId) {
        return configs.get(Strings.trimToEmpty(dataId));
    }

    /** 监听的分组。 */
    public String group() {
        return group;
    }

    /** 监听的所有 dataId。 */
    public List<String> dataIds() {
        return Collections.unmodifiableList(dataIds);
    }

    /** 停止监听并释放底层订阅连接。 */
    @Override
    public void close() {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        client.logger().info("waymark: 关闭配置监听 namespace={} group={}", namespace, group);
        subscription.close();
    }

    /** 处理配置变更事件：拉取最新配置并回调。 */
    private void handleEvent(Event event) {
        if (!WaymarkConstants.EVENT_TYPE_CONFIG.equals(event.eventType)) {
            return;
        }
        String dataId = Strings.trimToEmpty(event.watchKey);
        if (dataId.isEmpty() || !dataIds.contains(dataId)) {
            return;
        }
        ConfigItem item;
        try {
            item = ConfigApi.getConfig(client, namespace, group, dataId);
        } catch (RuntimeException e) {
            client.logger().error("waymark: 拉取变更配置 {} 失败: {}", dataId, e.getMessage());
            reportError(new WaymarkException("waymark: 拉取变更配置 " + dataId + " 失败: " + e.getMessage(), e));
            return;
        }
        client.logger().info("waymark: 配置 {} 发生变更", dataId);
        store(dataId, item);
        onChange.accept(item);
    }

    private void store(String dataId, ConfigItem item) {
        if (item == null || dataId == null || dataId.isEmpty()) {
            return;
        }
        configs.put(dataId, item);
    }

    private void reportError(Throwable error) {
        if (onError != null && error != null) {
            onError.accept(error);
        }
    }

    /** 去除空白、忽略空值并去重，保持原有顺序。 */
    private static List<String> normalizeValues(List<String> values) {
        List<String> out = new ArrayList<String>();
        if (values == null) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<String>();
        for (String value : values) {
            String key = Strings.trimToEmpty(value);
            if (key.isEmpty()) {
                continue;
            }
            seen.add(key);
        }
        out.addAll(seen);
        return out;
    }
}
