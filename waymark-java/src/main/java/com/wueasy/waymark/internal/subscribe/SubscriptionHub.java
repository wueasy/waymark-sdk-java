package com.wueasy.waymark.internal.subscribe;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.wueasy.waymark.internal.transport.TransportClient;
import com.wueasy.waymark.model.Event;
import com.wueasy.waymark.model.SubscribeOptions;

/**
 * 订阅连接管理器：按命名空间与分组复用同一条 SSE 连接。
 *
 * <p>服务端的一条连接同时订阅配置与实例变更，因此配置监听与实例订阅可注册到同一条连接上，
 * 各自按事件类型与 dataId 过滤。连接实际订阅的 dataId 为所有注册项关注的并集，并集变化时自动重建连接；
 * 同组内最后一个注册项注销时，连接关闭。使用 {@link #close()} 关闭全部连接。
 */
public final class SubscriptionHub {

    private final TransportClient client;

    private final Map<String, SharedSubscription> subscriptions =
            new HashMap<String, SharedSubscription>();

    public SubscriptionHub(TransportClient client) {
        this.client = client;
    }

    /**
     * 注册一个事件处理函数，复用（或按需创建）同一命名空间与分组下的订阅连接。
     *
     * @param dataIds 该处理函数关注的配置文件名；为空表示只关注实例变更。连接实际订阅的 dataId
     *                为当前所有处理函数 dataId 的并集
     * @return 用于注销的 {@link Lease}；同一分组最后一个处理函数注销时连接关闭
     */
    public Lease subscribe(String namespace, String group, long reconnectDelayMillis,
            Collection<String> dataIds, Consumer<Event> handler) {
        String key = key(namespace, group);
        List<String> keys = normalize(dataIds);
        SharedSubscription shared;
        synchronized (subscriptions) {
            shared = subscriptions.get(key);
            if (shared == null) {
                shared = new SharedSubscription(client, namespace, group, reconnectDelayMillis);
                subscriptions.put(key, shared);
            }
            shared.add(handler, keys);
        }
        return new Lease(this, key, shared, handler);
    }

    /** 关闭全部共享订阅连接并清空注册表。 */
    public void close() {
        List<SharedSubscription> all;
        synchronized (subscriptions) {
            all = new ArrayList<SharedSubscription>(subscriptions.values());
            subscriptions.clear();
        }
        for (SharedSubscription shared : all) {
            shared.close();
        }
    }

    private void release(String key, SharedSubscription shared, Consumer<Event> handler) {
        synchronized (subscriptions) {
            if (!shared.remove(handler)) {
                return;
            }
            if (!shared.isEmpty()) {
                return;
            }
            if (subscriptions.get(key) == shared) {
                subscriptions.remove(key);
            }
            shared.close();
        }
    }

    private static String key(String namespace, String group) {
        return namespace + "\u0000" + group;
    }

    /** 去除空白、忽略空值并去重，保持原有顺序。 */
    private static List<String> normalize(Collection<String> values) {
        List<String> out = new ArrayList<String>();
        if (values == null) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String key = value.trim();
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            out.add(key);
        }
        return out;
    }

    /** 一个注册项：close 时从共享连接注销。 */
    public static final class Lease implements Closeable {

        private final SubscriptionHub hub;

        private final String key;

        private final SharedSubscription shared;

        private final Consumer<Event> handler;

        private volatile boolean closed;

        private Lease(SubscriptionHub hub, String key, SharedSubscription shared,
                Consumer<Event> handler) {
            this.hub = hub;
            this.key = key;
            this.shared = shared;
            this.handler = handler;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            hub.release(key, shared, handler);
        }
    }

    /** 同一命名空间与分组下共享的订阅连接及各处理函数关注的 dataId。 */
    private static final class SharedSubscription {

        private final TransportClient client;

        private final String namespace;

        private final String group;

        private final long reconnectDelayMillis;

        private final Object lock = new Object();

        /** 处理函数 -> 其关注的 dataId。 */
        private final Map<Consumer<Event>, List<String>> handlers =
                new LinkedHashMap<Consumer<Event>, List<String>>();

        /** 当前连接实际订阅的 dataId。 */
        private List<String> activeDataIds = new ArrayList<String>();

        private Subscription subscription;

        private SharedSubscription(TransportClient client, String namespace, String group,
                long reconnectDelayMillis) {
            this.client = client;
            this.namespace = namespace;
            this.group = group;
            this.reconnectDelayMillis = reconnectDelayMillis;
        }

        void add(Consumer<Event> handler, List<String> dataIds) {
            synchronized (lock) {
                handlers.put(handler, dataIds);
                rebuildIfNeeded();
            }
        }

        /** 注销处理函数；返回是否存在该处理函数。 */
        boolean remove(Consumer<Event> handler) {
            synchronized (lock) {
                if (handlers.remove(handler) == null) {
                    return false;
                }
                if (!handlers.isEmpty()) {
                    rebuildIfNeeded();
                }
                return true;
            }
        }

        boolean isEmpty() {
            synchronized (lock) {
                return handlers.isEmpty();
            }
        }

        void close() {
            synchronized (lock) {
                if (subscription != null) {
                    subscription.close();
                    subscription = null;
                }
            }
        }

        /** 建立或重建连接：仅在连接未建立，或所有处理函数 dataId 并集发生变化时。 */
        private void rebuildIfNeeded() {
            List<String> merged = mergedDataIds();
            if (subscription == null) {
                activeDataIds = merged;
                subscription = create();
                return;
            }
            if (merged.equals(activeDataIds)) {
                return;
            }
            activeDataIds = merged;
            subscription.close();
            subscription = create();
        }

        /** 当前所有处理函数关注 dataId 的并集。 */
        private List<String> mergedDataIds() {
            Set<String> merged = new LinkedHashSet<String>();
            for (List<String> dataIds : handlers.values()) {
                merged.addAll(dataIds);
            }
            return new ArrayList<String>(merged);
        }

        private Subscription create() {
            SubscribeOptions options = new SubscribeOptions();
            options.namespace = namespace;
            options.groupName = group;
            options.reconnectDelayMillis = reconnectDelayMillis;
            options.dataIds = new ArrayList<String>(activeDataIds);
            options.handler = new Consumer<Event>() {
                @Override
                public void accept(Event event) {
                    dispatch(event);
                }
            };
            return Subscription.start(client, options);
        }

        /** 把事件分派给当前全部处理函数。 */
        private void dispatch(Event event) {
            List<Consumer<Event>> snapshot;
            synchronized (lock) {
                snapshot = new ArrayList<Consumer<Event>>(handlers.keySet());
            }
            for (Consumer<Event> handler : snapshot) {
                try {
                    handler.accept(event);
                } catch (RuntimeException e) {
                    client.logger().error("waymark: 处理订阅事件失败: {}", e.getMessage());
                }
            }
        }
    }
}
