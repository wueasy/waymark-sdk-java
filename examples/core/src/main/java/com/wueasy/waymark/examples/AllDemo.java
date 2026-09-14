package com.wueasy.waymark.examples;

import java.util.List;

import com.wueasy.waymark.WaymarkClient;
import com.wueasy.waymark.WaymarkConstants;
import com.wueasy.waymark.model.ConfigItem;
import com.wueasy.waymark.model.Instance;
import com.wueasy.waymark.model.InstanceRequest;
import com.wueasy.waymark.quickstart.ConfigWatcher;
import com.wueasy.waymark.quickstart.ConfigWatcherOptions;
import com.wueasy.waymark.quickstart.RegistryResolver;
import com.wueasy.waymark.quickstart.RegistryResolverOptions;
import com.wueasy.waymark.quickstart.SelectedInstance;

/**
 * 组合示例（快速初始化）：把 ConfigWatcher 与 RegistryResolver 一起使用。
 * ConfigWatcher 负责配置加载与变更回调，RegistryResolver 负责服务发现与自身注册，
 * 是业务服务接入 Waymark 的最小骨架。
 *
 * <p>运行：
 *
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.wueasy.waymark.examples.AllDemo
 * </pre>
 *
 * 程序常驻运行，按 Ctrl+C 退出并自动注销已注册实例。
 */
public final class AllDemo {

    public static void main(String[] args) throws Exception {
        DemoSupport.Args parsed = DemoSupport.Args.parse(args);
        DemoSupport.Options opts = DemoSupport.Options.bind(parsed);
        final String group = parsed.get("group", DemoSupport.env("WAYMARK_GROUP", DemoSupport.DEMO_GROUP));
        List<String> dataIds = DemoSupport.splitList(parsed.get(
                "dataIds", DemoSupport.DEMO_DATA_ID + "," + DemoSupport.DEMO_APP_DATA_ID));
        final String service = parsed.get("service", DemoSupport.DEMO_QUICK_SERVICE);
        String ip = parsed.get("ip", DemoSupport.env("WAYMARK_SELF_IP", DemoSupport.DEMO_IP));
        int port = parsed.getInt("port", DemoSupport.DEMO_QUICK_PORT);
        double weight = parsed.getDouble("weight", 1);

        if (dataIds.isEmpty()) {
            DemoSupport.println("dataIds 不能为空");
            return;
        }

        final WaymarkClient client = opts.newClient();
        DemoSupport.printf("== 创建客户端成功: endpoint=%s namespace=%s ==%n", opts.endpoint, opts.namespace);

        // 配置监听：一次初始化即完成加载 + 订阅 + 回调。
        ConfigWatcherOptions watcherOptions = new ConfigWatcherOptions();
        watcherOptions.namespace = opts.namespace;
        watcherOptions.groupName = group;
        watcherOptions.dataIds = dataIds;
        watcherOptions.onChange = new java.util.function.Consumer<ConfigItem>() {
            @Override
            public void accept(ConfigItem item) {
                DemoSupport.printf("[配置回调] dataId=%s type=%s md5=%s%n", item.dataId, item.type, item.md5);
            }
        };
        watcherOptions.onError = new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                DemoSupport.printf("[错误] 配置监听: %s%n", error.getMessage());
            }
        };
        final ConfigWatcher watcher = client.newConfigWatcher(watcherOptions);

        // 服务发现：在线实例缓存到内存，内置自身注册与心跳。
        RegistryResolverOptions resolverOptions = new RegistryResolverOptions();
        resolverOptions.namespace = opts.namespace;
        resolverOptions.groupName = group;
        resolverOptions.onError = new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                DemoSupport.printf("[错误] 服务发现: %s%n", error.getMessage());
            }
        };
        resolverOptions.self = new InstanceRequest()
                .namespace(opts.namespace)
                .groupName(group)
                .serviceName(service)
                .clusterName(WaymarkConstants.DEFAULT_CLUSTER)
                .ip(ip)
                .port(port)
                .weight(weight)
                .putMetadata("version", "1.0.0");
        final RegistryResolver resolver = client.newRegistryResolver(resolverOptions);

        DemoSupport.onShutdown(new Runnable() {
            @Override
            public void run() {
                resolver.close();
                watcher.close();
                client.close();
            }
        });

        // 等待自身实例注册生效。
        Thread.sleep(1000L);

        DemoSupport.println("");
        DemoSupport.println("快速初始化运行中（配置监听 + 服务发现）。按 Ctrl+C 退出。");
        while (true) {
            Thread.sleep(2000L);
            List<Instance> instances = resolver.instances(service);
            DemoSupport.printf("[发现] 服务 %s 在线实例 %d 个%n", service, instances.size());
            try {
                SelectedInstance picked = resolver.pick(service);
                DemoSupport.printf("[选择] url=%s currentWeight=%.2f%n", picked.url, picked.currentWeight);
            } catch (RuntimeException e) {
                DemoSupport.printf("[错误] 选择实例: %s%n", e.getMessage());
            }
        }
    }

    private AllDemo() {
    }
}
