package com.wueasy.waymark.examples;

import java.util.Collections;
import java.util.List;

import com.wueasy.waymark.WaymarkClient;
import com.wueasy.waymark.WaymarkConstants;
import com.wueasy.waymark.internal.subscribe.Subscription;
import com.wueasy.waymark.model.ConfigHistory;
import com.wueasy.waymark.model.ConfigItem;
import com.wueasy.waymark.model.ConfigPage;
import com.wueasy.waymark.model.Event;
import com.wueasy.waymark.model.ExportItem;
import com.wueasy.waymark.model.ExportOptions;
import com.wueasy.waymark.model.Instance;
import com.wueasy.waymark.model.InstanceRequest;
import com.wueasy.waymark.model.ListConfigsOptions;
import com.wueasy.waymark.model.RegisterInstanceResult;
import com.wueasy.waymark.model.ServiceSummary;
import com.wueasy.waymark.model.SubscribeOptions;
import com.wueasy.waymark.quickstart.ConfigWatcher;
import com.wueasy.waymark.quickstart.ConfigWatcherOptions;
import com.wueasy.waymark.quickstart.RegistryResolver;
import com.wueasy.waymark.quickstart.RegistryResolverOptions;
import com.wueasy.waymark.quickstart.SelectedInstance;

/**
 * 完整示例：演示 Waymark Java 客户端 SDK 的常见用法，包含
 * 配置中心（读取/列表/历史/导出）、注册中心（注册/心跳/查询）、
 * 变更订阅，以及快速初始化的配置监听与服务发现。
 *
 * <p>运行：
 *
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.wueasy.waymark.examples.FullDemo
 * </pre>
 *
 * 示例执行完配置中心演示后会注册实例并保持在线（后台定时心跳），
 * 同时持续订阅配置与实例变更，并常驻运行，便于调试观察；按 Ctrl+C 退出时会自动注销实例。
 */
public final class FullDemo {

    public static void main(String[] args) throws Exception {
        DemoSupport.Args parsed = DemoSupport.Args.parse(args);
        DemoSupport.Options opts = DemoSupport.Options.bind(parsed);
        final String namespace = opts.namespace;

        final WaymarkClient client = opts.newClient();
        DemoSupport.printf("== 创建客户端成功: endpoint=%s namespace=%s ==%n", opts.endpoint, namespace);

        try {
            demoConfig(client, namespace);
        } catch (RuntimeException e) {
            DemoSupport.printf("配置中心示例失败: %s%n", e.getMessage());
            client.close();
            return;
        }

        final Runnable deregister;
        try {
            deregister = demoRegistry(client, namespace);
        } catch (RuntimeException e) {
            DemoSupport.printf("注册中心示例失败: %s%n", e.getMessage());
            client.close();
            return;
        }

        final Subscription subscription;
        try {
            subscription = demoWatch(client, namespace);
        } catch (RuntimeException e) {
            DemoSupport.printf("订阅示例失败: %s%n", e.getMessage());
            deregister.run();
            client.close();
            return;
        }

        // 快速初始化：配置监听 + 服务发现两个开箱即用组件（内置自身注册与心跳）。
        final ConfigWatcher watcher;
        final RegistryResolver resolver;
        try {
            watcher = newConfigWatcher(client, namespace);
            resolver = newRegistryResolver(client, namespace);
        } catch (RuntimeException e) {
            subscription.close();
            deregister.run();
            client.close();
            DemoSupport.printf("快速初始化示例失败: %s%n", e.getMessage());
            return;
        }

        Thread.sleep(1000L);
        List<Instance> instances = resolver.instances(DemoSupport.DEMO_QUICK_SERVICE);
        DemoSupport.printf("[快速初始化] 服务 %s 在线实例 %d 个%n",
                DemoSupport.DEMO_QUICK_SERVICE, instances.size());
        for (int i = 0; i < 3; i++) {
            try {
                SelectedInstance picked = resolver.pick(DemoSupport.DEMO_QUICK_SERVICE);
                DemoSupport.printf("[快速初始化] 选择实例: url=%s currentWeight=%.2f%n",
                        picked.url, picked.currentWeight);
            } catch (RuntimeException e) {
                DemoSupport.printf("[快速初始化] 选择实例失败: %s%n", e.getMessage());
                break;
            }
        }

        DemoSupport.onShutdown(new Runnable() {
            @Override
            public void run() {
                resolver.close();
                watcher.close();
                subscription.close();
                deregister.run();
                client.close();
            }
        });

        DemoSupport.println("");
        DemoSupport.println("示例持续运行中（实例定时心跳、配置变更订阅）。按 Ctrl+C 退出。");
        DemoSupport.awaitShutdown(null);
    }

    /** 演示配置中心：读取、列表、历史与导出。 */
    private static void demoConfig(WaymarkClient client, String namespace) {
        DemoSupport.println("");
        DemoSupport.println("-- 配置中心 --");

        ConfigItem item = client.getConfig(namespace, DemoSupport.DEMO_GROUP, DemoSupport.DEMO_DATA_ID);
        DemoSupport.printf("读取配置成功: type=%s md5=%s%n%s", item.type, item.md5, item.contentOrEmpty());

        ListConfigsOptions listOptions = new ListConfigsOptions();
        listOptions.namespace = namespace;
        listOptions.groupName = DemoSupport.DEMO_GROUP;
        listOptions.pageNum = 1;
        listOptions.pageSize = 10;
        ConfigPage page = client.listConfigs(listOptions);
        DemoSupport.printf("查询配置列表成功: 共 %d 条，本页 %d 条%n",
                page.total, page.list == null ? 0 : page.list.size());

        List<ConfigHistory> histories = client.configHistory(namespace, DemoSupport.DEMO_GROUP,
                DemoSupport.DEMO_DATA_ID);
        DemoSupport.printf("查询配置历史成功: 共 %d 个版本%n", histories.size());

        ExportOptions exportOptions = new ExportOptions();
        exportOptions.namespace = namespace;
        ExportItem exportItem = new ExportItem();
        exportItem.groupName = DemoSupport.DEMO_GROUP;
        exportItem.dataId = DemoSupport.DEMO_DATA_ID;
        exportOptions.items = Collections.singletonList(exportItem);
        byte[] data = client.exportConfigs(exportOptions);
        DemoSupport.printf("导出配置成功: %d 字节%n", data.length);
    }

    /**
     * 演示注册中心：注册实例并保持在线（后台定时心跳），返回退出时使用的注销任务。
     */
    private static Runnable demoRegistry(final WaymarkClient client, final String namespace) {
        DemoSupport.println("");
        DemoSupport.println("-- 注册中心 --");

        RegisterInstanceResult result = client.registerInstance(instanceRequest(namespace, "1.0.0"));
        DemoSupport.printf("注册实例成功: created=%s id=%d（临时实例，服务端默认 15s 心跳超时）%n",
                result.created, result.instance.id);

        client.beat(namespace, DemoSupport.DEMO_GROUP, DemoSupport.DEMO_SERVICE,
                DemoSupport.DEMO_IP, DemoSupport.DEMO_PORT);
        DemoSupport.println("发送心跳成功");

        List<Instance> instances = client.listInstances(namespace, DemoSupport.DEMO_GROUP,
                DemoSupport.DEMO_SERVICE);
        DemoSupport.printf("查询实例列表成功: 共 %d 个实例%n", instances.size());

        List<ServiceSummary> services = client.listServices(namespace, DemoSupport.DEMO_GROUP);
        DemoSupport.printf("查询服务列表成功: 共 %d 个服务%n", services.size());

        // 后台定时心跳，保持实例在线（实例未注销前一直续约）。
        final boolean[] running = new boolean[] {true};
        Thread heartbeat = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running[0]) {
                    try {
                        Thread.sleep(DemoSupport.DEMO_HEARTBEAT_INTERVAL_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (!running[0]) {
                        return;
                    }
                    try {
                        client.beat(namespace, DemoSupport.DEMO_GROUP, DemoSupport.DEMO_SERVICE,
                                DemoSupport.DEMO_IP, DemoSupport.DEMO_PORT);
                    } catch (RuntimeException e) {
                        DemoSupport.printf("发送心跳失败: %s%n", e.getMessage());
                    }
                }
            }
        }, "waymark-example-heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();

        return new Runnable() {
            @Override
            public void run() {
                running[0] = false;
                heartbeat.interrupt();
                try {
                    client.deregisterInstance(namespace, DemoSupport.DEMO_GROUP,
                            DemoSupport.DEMO_SERVICE, DemoSupport.DEMO_IP, DemoSupport.DEMO_PORT);
                    DemoSupport.println("注销实例成功");
                } catch (RuntimeException e) {
                    DemoSupport.printf("注销实例失败: %s%n", e.getMessage());
                }
            }
        };
    }

    /** 组装手动注册示例使用的实例信息。 */
    private static InstanceRequest instanceRequest(String namespace, String version) {
        return new InstanceRequest()
                .namespace(namespace)
                .groupName(DemoSupport.DEMO_GROUP)
                .serviceName(DemoSupport.DEMO_SERVICE)
                .clusterName(WaymarkConstants.DEFAULT_CLUSTER)
                .ip(DemoSupport.DEMO_IP)
                .port(DemoSupport.DEMO_PORT)
                .weight(1)
                .putMetadata("version", version);
    }

    /**
     * 演示变更订阅：只订阅、不发布配置，持续订阅配置与实例变更，
     * 收到事件后拉取并打印订阅的配置信息与在线实例列表。
     */
    private static Subscription demoWatch(WaymarkClient client, final String namespace) {
        DemoSupport.println("");
        DemoSupport.println("-- 变更订阅 --");

        // 单条订阅连接同时订阅多个配置文件与实例变更，按事件类型区分处理。
        SubscribeOptions subscribeOptions = new SubscribeOptions();
        subscribeOptions.namespace = namespace;
        subscribeOptions.groupName = DemoSupport.DEMO_GROUP;
        subscribeOptions.dataIds = DemoSupport.splitList(
                DemoSupport.DEMO_DATA_ID + "," + DemoSupport.DEMO_APP_DATA_ID);
        subscribeOptions.serviceName = DemoSupport.DEMO_SERVICE;
        subscribeOptions.handler = new java.util.function.Consumer<Event>() {
            @Override
            public void accept(Event event) {
                if (WaymarkConstants.EVENT_TYPE_CONFIG.equals(event.eventType)) {
                    DemoSupport.printf("收到配置变更事件: key=%s md5=%s%n", event.watchKey, event.md5);
                    logInstances(client, namespace);
                } else if (WaymarkConstants.EVENT_TYPE_INSTANCE.equals(event.eventType)) {
                    DemoSupport.printf("收到实例变更事件: key=%s%n", event.watchKey);
                    logInstances(client, namespace);
                }
            }
        };
        Subscription subscription = client.watch(subscribeOptions);

        // 等待订阅连接建立。
        try {
            Thread.sleep(500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 先打印一次订阅范围内的当前数据，便于确认订阅内容。
        logConfigs(client, namespace);
        logInstances(client, namespace);

        return subscription;
    }

    private static void logConfigs(WaymarkClient client, String namespace) {
        ListConfigsOptions options = new ListConfigsOptions();
        options.namespace = namespace;
        options.groupName = DemoSupport.DEMO_GROUP;
        options.pageNum = 1;
        options.pageSize = 50;
        ConfigPage page = client.listConfigs(options);
        DemoSupport.printf("订阅配置 [%s/%s] 共 %d 条:%n", namespace, DemoSupport.DEMO_GROUP, page.total);
        if (page.list == null) {
            return;
        }
        for (ConfigItem item : page.list) {
            DemoSupport.printf("  - dataId=%s type=%s md5=%s%n", item.dataId, item.type, item.md5);
            if (DemoSupport.DEMO_DATA_ID.equals(item.dataId)) {
                DemoSupport.printf("    内容:%n%s", DemoSupport.indent(item.content, "    "));
            }
        }
    }

    private static void logInstances(WaymarkClient client, String namespace) {
        List<Instance> instances = client.listInstances(namespace, DemoSupport.DEMO_GROUP,
                DemoSupport.DEMO_SERVICE);
        int online = 0;
        for (Instance instance : instances) {
            if (instance.healthy == 1) {
                online++;
            }
        }
        DemoSupport.printf("订阅服务 [%s/%s/%s] 实例 %d 个，在线 %d 个:%n",
                namespace, DemoSupport.DEMO_GROUP, DemoSupport.DEMO_SERVICE, instances.size(), online);
        for (Instance instance : instances) {
            String status = instance.healthy == 1 ? "在线" : "不健康";
            DemoSupport.printf("  - %s:%d %s weight=%.1f cluster=%s meta=%s%n",
                    instance.ip, instance.port, status, instance.weight, instance.clusterName, instance.metadata);
        }
    }

    /** 快速初始化配置监听。 */
    private static ConfigWatcher newConfigWatcher(WaymarkClient client, String namespace) {
        DemoSupport.println("");
        DemoSupport.println("-- 快速初始化 --");
        DemoSupport.println("[快速初始化] 配置监听器已创建");
        ConfigWatcherOptions options = new ConfigWatcherOptions();
        options.namespace = namespace;
        options.groupName = DemoSupport.DEMO_GROUP;
        options.dataIds = DemoSupport.splitList(DemoSupport.DEMO_DATA_ID + "," + DemoSupport.DEMO_APP_DATA_ID);
        options.onChange = new java.util.function.Consumer<ConfigItem>() {
            @Override
            public void accept(ConfigItem item) {
                DemoSupport.printf("[快速初始化] 配置回调: dataId=%s type=%s md5=%s%n",
                        item.dataId, item.type, item.md5);
            }
        };
        options.onError = new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                DemoSupport.printf("[快速初始化] 配置监听错误: %s%n", error.getMessage());
            }
        };
        return client.newConfigWatcher(options);
    }

    /** 快速初始化服务发现（内置自身注册与心跳）。 */
    private static RegistryResolver newRegistryResolver(WaymarkClient client, String namespace) {
        RegistryResolverOptions options = new RegistryResolverOptions();
        options.namespace = namespace;
        options.groupName = DemoSupport.DEMO_GROUP;
        options.self = new InstanceRequest()
                .namespace(namespace)
                .groupName(DemoSupport.DEMO_GROUP)
                .serviceName(DemoSupport.DEMO_QUICK_SERVICE)
                .clusterName(WaymarkConstants.DEFAULT_CLUSTER)
                .ip(DemoSupport.DEMO_IP)
                .port(DemoSupport.DEMO_QUICK_PORT)
                .weight(1)
                .putMetadata("version", "1.0.0");
        options.onError = new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                DemoSupport.printf("[快速初始化] 服务发现错误: %s%n", error.getMessage());
            }
        };
        return client.newRegistryResolver(options);
    }

    private FullDemo() {
    }
}
