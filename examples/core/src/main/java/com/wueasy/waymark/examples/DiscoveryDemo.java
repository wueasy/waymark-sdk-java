package com.wueasy.waymark.examples;

import java.util.List;

import com.wueasy.waymark.WaymarkClient;
import com.wueasy.waymark.WaymarkConstants;
import com.wueasy.waymark.model.Instance;
import com.wueasy.waymark.model.InstanceRequest;
import com.wueasy.waymark.quickstart.RegistryResolver;
import com.wueasy.waymark.quickstart.RegistryResolverOptions;
import com.wueasy.waymark.quickstart.SelectedInstance;

/**
 * 服务发现示例（快速初始化 RegistryResolver）：
 * 在线实例缓存到内存、自动订阅实例变更，并内置自身实例的注册与心跳
 * （默认每 5s 一次，退出时自动注销）。调用方只需按服务名取值，
 * 即可拿到存活链接与计算好的权重。
 *
 * <p>运行：
 *
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.wueasy.waymark.examples.DiscoveryDemo
 * </pre>
 *
 * 程序常驻运行并每 2 秒按服务名选一次实例（平滑加权轮询），按 Ctrl+C 退出。
 */
public final class DiscoveryDemo {

    public static void main(String[] args) throws Exception {
        DemoSupport.Args parsed = DemoSupport.Args.parse(args);
        DemoSupport.Options opts = DemoSupport.Options.bind(parsed);
        final String group = parsed.get("group", DemoSupport.env("WAYMARK_GROUP", DemoSupport.DEMO_GROUP));
        final String service = parsed.get("service", DemoSupport.DEMO_SERVICE);
        String ip = parsed.get("ip", DemoSupport.env("WAYMARK_SELF_IP", DemoSupport.DEMO_IP));
        int port = parsed.getInt("port", DemoSupport.DEMO_QUICK_PORT);
        double weight = parsed.getDouble("weight", 1);
        boolean register = parsed.getBool("register", true);

        final WaymarkClient client = opts.newClient();
        DemoSupport.printf("== 创建客户端成功: endpoint=%s namespace=%s ==%n", opts.endpoint, opts.namespace);

        RegistryResolverOptions resolverOptions = new RegistryResolverOptions();
        resolverOptions.namespace = opts.namespace;
        resolverOptions.groupName = group;
        resolverOptions.onError = new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                DemoSupport.printf("[错误] 服务发现: %s%n", error.getMessage());
            }
        };
        if (register) {
            // 内置自身注册与心跳，无需手动调用 registerInstance/beat。
            resolverOptions.self = new InstanceRequest()
                    .namespace(opts.namespace)
                    .groupName(group)
                    .serviceName(service)
                    .clusterName(WaymarkConstants.DEFAULT_CLUSTER)
                    .ip(ip)
                    .port(port)
                    .weight(weight)
                    .putMetadata("version", "1.0.0");
        }

        final RegistryResolver resolver = client.newRegistryResolver(resolverOptions);

        // 等待自身实例注册生效。
        Thread.sleep(1000L);
        logInstances(resolver, service);

        // 退出时关闭解析器（注销自身实例）并释放客户端。
        DemoSupport.onShutdown(new Runnable() {
            @Override
            public void run() {
                resolver.close();
                client.close();
            }
        });

        DemoSupport.println("");
        DemoSupport.println("服务发现运行中，每 2 秒选择一次实例。按 Ctrl+C 退出。");
        while (true) {
            Thread.sleep(2000L);
            logInstances(resolver, service);
            try {
                SelectedInstance picked = resolver.pick(service);
                DemoSupport.printf("[选择] service=%s url=%s currentWeight=%.2f%n",
                        service, picked.url, picked.currentWeight);
            } catch (RuntimeException e) {
                DemoSupport.printf("[错误] 选择实例: %s%n", e.getMessage());
            }
        }
    }

    /** 打印解析器内存中缓存的在线实例。 */
    private static void logInstances(RegistryResolver resolver, String service) {
        List<Instance> instances = resolver.instances(service);
        DemoSupport.printf("[缓存] 服务 %s 在线实例 %d 个:%n", service, instances.size());
        for (Instance instance : instances) {
            DemoSupport.printf("  - %s:%d weight=%.1f cluster=%s meta=%s%n",
                    instance.ip, instance.port, instance.weight, instance.clusterName, instance.metadata);
        }
    }

    private DiscoveryDemo() {
    }
}
