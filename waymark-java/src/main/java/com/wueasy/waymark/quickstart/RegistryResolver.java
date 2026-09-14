package com.wueasy.waymark.quickstart;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.wueasy.waymark.WaymarkConstants;
import com.wueasy.waymark.WaymarkException;
import com.wueasy.waymark.internal.cache.ConfigCache;
import com.wueasy.waymark.internal.registry.RegistryApi;
import com.wueasy.waymark.internal.subscribe.SubscriptionHub;
import com.wueasy.waymark.internal.transport.TransportClient;
import com.wueasy.waymark.internal.util.Strings;
import com.wueasy.waymark.internal.util.Threads;
import com.wueasy.waymark.model.Event;
import com.wueasy.waymark.model.Instance;
import com.wueasy.waymark.model.InstanceRequest;

/**
 * 服务发现解析器：将在线实例缓存到内存，提供按服务名的平滑加权轮询（SWRR）选择，
 * 并可选自动注册、保持自身实例在线。使用 {@link #close()} 释放。
 */
public final class RegistryResolver implements Closeable {

    private final TransportClient client;

    private final String namespace;

    private final String group;

    /** 自身实例信息，非空时自动注册并保持心跳。 */
    private final InstanceRequest self;

    private final long heartbeatMillis;

    private final long refreshMillis;

    private final Consumer<Throwable> onError;

    private final ReentrantLock lock = new ReentrantLock();

    /** 服务名 -> 实例调度状态列表。 */
    private final Map<String, List<InstanceState>> services = new HashMap<String, List<InstanceState>>();

    private final SubscriptionHub.Lease subscription;

    private final Thread refreshThread;

    private final Thread keepAliveThread;

    private final Object closeLock = new Object();

    private volatile boolean running = true;

    private volatile boolean registered;

    private boolean closed;

    private RegistryResolver(TransportClient client, RegistryResolverOptions options, InstanceRequest self) {
        this.client = client;
        this.namespace = ConfigCache.normalizeKey(options.namespace, WaymarkConstants.DEFAULT_NAMESPACE);
        this.group = ConfigCache.normalizeKey(options.groupName, WaymarkConstants.DEFAULT_GROUP);
        this.self = self;
        this.heartbeatMillis = options.heartbeatIntervalMillis > 0
                ? options.heartbeatIntervalMillis
                : WaymarkConstants.DEFAULT_HEARTBEAT_INTERVAL_MILLIS;
        this.refreshMillis = options.refreshIntervalMillis > 0
                ? options.refreshIntervalMillis
                : WaymarkConstants.DEFAULT_REFRESH_INTERVAL_MILLIS;
        this.onError = options.onError;

        if (self != null) {
            client.logger().info("waymark: 启动服务发现解析器 namespace={} group={} self={}:{}",
                    namespace, group, self.getIp(), self.getPort());
        } else {
            client.logger().info("waymark: 启动服务发现解析器 namespace={} group={}", namespace, group);
        }

        // 初始化：拉取一次在线实例；失败不阻断启动，交由后台刷新兜底。
        try {
            refreshAll();
        } catch (RuntimeException e) {
            client.logger().warn("waymark: 加载在线实例失败: {}", e.getMessage());
            reportError(new WaymarkException("waymark: 加载在线实例失败: " + e.getMessage(), e));
        }

        // 订阅该分组下全部服务的实例变更；不订阅任何配置，与同分组的配置监听复用同一条 SSE 连接。
        this.subscription = client.sharedSubscriptions().subscribe(namespace, group,
                options.reconnectDelayMillis, null, new Consumer<Event>() {
                    @Override
                    public void accept(Event event) {
                        handleEvent(event);
                    }
                });

        // 定时全量刷新，兜底补偿丢失的变更事件。
        this.refreshThread = Threads.daemonFactory("waymark-resolver-refresh").newThread(new Runnable() {
            @Override
            public void run() {
                refreshLoop();
            }
        });
        refreshThread.start();

        // 自身实例注册与心跳。
        if (self != null) {
            this.keepAliveThread = Threads.daemonFactory("waymark-resolver-keepalive").newThread(new Runnable() {
                @Override
                public void run() {
                    keepAliveLoop();
                }
            });
            keepAliveThread.start();
        } else {
            this.keepAliveThread = null;
        }
    }

    /**
     * 创建服务发现解析器：拉取一次在线实例并缓存到内存，随后订阅实例变更并定时全量刷新；
     * self 非空时自动注册并保持心跳。
     */
    public static RegistryResolver create(TransportClient client, RegistryResolverOptions options) {
        InstanceRequest self = null;
        if (options.self != null) {
            self = normalizeSelf(client, options, options.self);
        }
        return new RegistryResolver(client, options, self);
    }

    /** 按服务名返回一个在线实例（平滑加权轮询），无可用实例时抛出异常。 */
    public SelectedInstance pick(String service) {
        String name = Strings.trimToEmpty(service);
        if (name.isEmpty()) {
            throw new WaymarkException("waymark: 服务名不能为空");
        }
        lock.lock();
        try {
            List<InstanceState> states = services.get(name);
            if (states == null || states.isEmpty()) {
                throw new WaymarkException("waymark: 服务 " + name + " 暂无在线实例");
            }
            // 平滑加权轮询：每个实例先累加自身权重，取当前权重最大者，选中后再减去总权重。
            double totalWeight = 0;
            InstanceState best = null;
            for (InstanceState state : states) {
                state.currentWeight += state.effectiveWeight;
                totalWeight += state.effectiveWeight;
                if (best == null || state.currentWeight > best.currentWeight) {
                    best = state;
                }
            }
            best.currentWeight -= totalWeight;
            SelectedInstance picked = new SelectedInstance(best.instance, instanceURL(best.instance),
                    best.effectiveWeight);
            client.logger().debug("waymark: 选中实例 service={} url={} weight={}", name, picked.url,
                    picked.currentWeight);
            return picked;
        } finally {
            lock.unlock();
        }
    }

    /** 按服务名返回一个在线实例的访问链接，形如 http://127.0.0.1:8080。 */
    public String pickURL(String service) {
        return pick(service).url;
    }

    /** 返回指定服务的在线实例快照，服务不存在时返回空列表。 */
    public List<Instance> instances(String service) {
        String name = Strings.trimToEmpty(service);
        lock.lock();
        try {
            List<InstanceState> states = services.get(name);
            List<Instance> out = new ArrayList<Instance>();
            if (states != null) {
                for (InstanceState state : states) {
                    out.add(state.instance);
                }
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /** 停止后台订阅与刷新；若注册过自身实例，会先注销。 */
    @Override
    public void close() {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
            running = false;
        }
        client.logger().info("waymark: 关闭服务发现解析器 namespace={} group={}", namespace, group);
        subscription.close();
        refreshThread.interrupt();
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
        }
        if (self != null && registered) {
            try {
                RegistryApi.deregisterInstance(client, self.getNamespace(), self.getGroupName(),
                        self.getServiceName(), self.getIp(), self.getPort());
                registered = false;
            } catch (RuntimeException e) {
                client.logger().warn("waymark: 注销自身实例失败: {}", e.getMessage());
            }
        }
    }

    /** 处理实例变更事件，刷新受影响服务的在线实例缓存。 */
    private void handleEvent(Event event) {
        if (!WaymarkConstants.EVENT_TYPE_INSTANCE.equals(event.eventType)) {
            return;
        }
        String service = Strings.trimToEmpty(event.watchKey);
        try {
            if (service.isEmpty()) {
                refreshAll();
            } else {
                refreshService(service);
            }
        } catch (RuntimeException e) {
            if (running) {
                client.logger().error("waymark: 刷新实例缓存失败: {}", e.getMessage());
                reportError(new WaymarkException("waymark: 刷新实例缓存失败: " + e.getMessage(), e));
            }
        }
    }

    /** 拉取该命名空间与分组下的全部实例，重建内存缓存。 */
    private void refreshAll() {
        List<Instance> instances = RegistryApi.listInstances(client, namespace, group, null);
        Map<String, List<Instance>> grouped = new HashMap<String, List<Instance>>();
        for (Instance instance : instances) {
            if (instance.healthy != 1) {
                continue;
            }
            List<Instance> bucket = grouped.get(instance.serviceName);
            if (bucket == null) {
                bucket = new ArrayList<Instance>();
                grouped.put(instance.serviceName, bucket);
            }
            bucket.add(instance);
        }
        lock.lock();
        try {
            for (Map.Entry<String, List<Instance>> entry : grouped.entrySet()) {
                services.put(entry.getKey(), buildStates(services.get(entry.getKey()), entry.getValue()));
            }
            // 已无在线实例的服务从缓存中移除。
            List<String> stale = new ArrayList<String>();
            for (String service : services.keySet()) {
                if (!grouped.containsKey(service)) {
                    stale.add(service);
                }
            }
            for (String service : stale) {
                services.remove(service);
            }
        } finally {
            lock.unlock();
        }
        client.logger().debug("waymark: 全量刷新实例缓存完成，在线服务数={}", grouped.size());
    }

    /** 拉取指定服务的在线实例，更新对应缓存。 */
    private void refreshService(String service) {
        List<Instance> instances = RegistryApi.listInstances(client, namespace, group, service);
        List<Instance> online = new ArrayList<Instance>();
        for (Instance instance : instances) {
            if (instance.healthy == 1) {
                online.add(instance);
            }
        }
        lock.lock();
        try {
            if (online.isEmpty()) {
                services.remove(service);
                client.logger().debug("waymark: 服务 {} 已无在线实例", service);
                return;
            }
            services.put(service, buildStates(services.get(service), online));
        } finally {
            lock.unlock();
        }
        client.logger().debug("waymark: 刷新服务 {} 实例完成，在线实例数={}", service, online.size());
    }

    /** 定时全量刷新实例缓存，直到解析器被关闭。 */
    private void refreshLoop() {
        while (running) {
            try {
                Thread.sleep(refreshMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) {
                return;
            }
            try {
                refreshAll();
            } catch (RuntimeException e) {
                if (running) {
                    client.logger().warn("waymark: 定时刷新实例缓存失败: {}", e.getMessage());
                    reportError(new WaymarkException("waymark: 刷新实例缓存失败: " + e.getMessage(), e));
                }
            }
        }
    }

    /** 注册自身实例并按固定间隔发送心跳，直到解析器被关闭。 */
    private void keepAliveLoop() {
        registerSelf();
        while (running) {
            try {
                Thread.sleep(heartbeatMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) {
                return;
            }
            beatSelf();
        }
    }

    /** 注册自身实例，成功后立即刷新该服务，保证自身实例尽快进入内存缓存。 */
    private void registerSelf() {
        try {
            RegistryApi.registerInstance(client, self);
        } catch (RuntimeException e) {
            registered = false;
            client.logger().error("waymark: 注册自身实例失败 service={} address={}:{}: {}",
                    self.getServiceName(), self.getIp(), self.getPort(), e.getMessage());
            reportError(new WaymarkException("waymark: 注册自身实例失败: " + e.getMessage(), e));
            return;
        }
        registered = true;
        client.logger().info("waymark: 自身实例注册成功 service={} address={}:{}",
                self.getServiceName(), self.getIp(), self.getPort());
        try {
            refreshService(self.getServiceName());
        } catch (RuntimeException e) {
            if (running) {
                client.logger().error("waymark: 刷新服务 {} 实例失败: {}", self.getServiceName(), e.getMessage());
                reportError(new WaymarkException(
                        "waymark: 刷新服务 " + self.getServiceName() + " 实例失败: " + e.getMessage(), e));
            }
        }
    }

    /** 发送自身实例心跳；失败通常意味着实例已被剔除，此时重新注册。 */
    private void beatSelf() {
        try {
            RegistryApi.beat(client, self.getNamespace(), self.getGroupName(), self.getServiceName(),
                    self.getIp(), self.getPort());
        } catch (RuntimeException e) {
            if (!running) {
                return;
            }
            client.logger().warn("waymark: 实例心跳失败，尝试重新注册: {}", e.getMessage());
            reportError(new WaymarkException("waymark: 实例心跳失败: " + e.getMessage(), e));
            registerSelf();
        }
    }

    private void reportError(Throwable error) {
        if (onError != null && error != null) {
            onError.accept(error);
        }
    }

    /** 构建实例调度状态；同实例沿用原有当前权重，避免刷新后调度突变。 */
    private static List<InstanceState> buildStates(List<InstanceState> prev, List<Instance> instances) {
        List<InstanceState> next = new ArrayList<InstanceState>(instances.size());
        for (Instance instance : instances) {
            double weight = instance.weight;
            if (weight <= 0) {
                weight = 1;
            }
            InstanceState state = new InstanceState(instance, weight);
            if (prev != null) {
                for (InstanceState old : prev) {
                    if (old.instance.ip != null && old.instance.ip.equals(instance.ip)
                            && old.instance.port == instance.port) {
                        state.currentWeight = old.currentWeight;
                        break;
                    }
                }
            }
            next.add(state);
        }
        return next;
    }

    /** 补全自身实例的默认字段并校验必填项，返回副本以避免修改调用方对象。 */
    private static InstanceRequest normalizeSelf(TransportClient client, RegistryResolverOptions options,
            InstanceRequest source) {
        if (Strings.isBlank(source.getServiceName())) {
            throw new WaymarkException("waymark: 自身实例的 serviceName 不能为空");
        }
        if (Strings.isBlank(source.getIp())) {
            throw new WaymarkException("waymark: 自身实例的 ip 不能为空");
        }
        if (source.getPort() <= 0) {
            throw new WaymarkException("waymark: 自身实例的 port 必须大于 0");
        }
        InstanceRequest self = new InstanceRequest();
        self.serviceName(source.getServiceName().trim());
        self.ip(source.getIp().trim());
        self.port(source.getPort());
        self.weight(source.getWeight());
        self.healthy(source.getHealthy());
        self.ephemeral(source.getEphemeral());
        self.metadata(source.getMetadata());
        self.namespace(Strings.isBlank(source.getNamespace())
                ? ConfigCache.normalizeKey(options.namespace, WaymarkConstants.DEFAULT_NAMESPACE)
                : source.getNamespace().trim());
        self.groupName(Strings.isBlank(source.getGroupName())
                ? ConfigCache.normalizeKey(options.groupName, WaymarkConstants.DEFAULT_GROUP)
                : source.getGroupName().trim());
        self.clusterName(Strings.isBlank(source.getClusterName())
                ? WaymarkConstants.DEFAULT_CLUSTER
                : source.getClusterName().trim());
        return self;
    }

    /** 拼接实例访问链接。 */
    private static String instanceURL(Instance instance) {
        String host = instance.ip == null ? "" : instance.ip;
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + instance.port;
    }

    /** 缓存中的实例及其平滑加权轮询状态。 */
    private static final class InstanceState {

        private final Instance instance;

        private final double effectiveWeight;

        private double currentWeight;

        private InstanceState(Instance instance, double effectiveWeight) {
            this.instance = instance;
            this.effectiveWeight = effectiveWeight;
        }
    }
}
