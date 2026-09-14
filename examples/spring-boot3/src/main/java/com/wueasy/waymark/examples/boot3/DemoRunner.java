package com.wueasy.waymark.examples.boot3;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.wueasy.waymark.WaymarkConstants;
import com.wueasy.waymark.model.Instance;
import com.wueasy.waymark.quickstart.RegistryResolver;
import com.wueasy.waymark.spring.boot.autoconfigure.WaymarkProperties;

/**
 * 示例运行入口，演示 starter 自动配置后的开箱用法：
 * <ol>
 *   <li>只订阅、不发布配置：在配置中心修改 {@code demo-app.yaml} 即可观察 {@link DemoProperties} 热加载，无需重启；</li>
 *   <li>周期性通过 {@link RegistryResolver} 查看自身服务在线实例并按权重选择实例，验证自动注册与服务发现。</li>
 * </ol>
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoRunner.class);

    /** 周期性查看服务发现的间隔（毫秒）。 */
    private static final long TICK_MILLIS = 5000L;

    private final RegistryResolver resolver;

    private final WaymarkProperties properties;

    public DemoRunner(RegistryResolver resolver, WaymarkProperties properties) {
        this.resolver = resolver;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        final String namespace = orDefault(properties.getConfig().getNamespace(), WaymarkConstants.DEFAULT_NAMESPACE);
        final String group = orDefault(properties.getConfig().getGroupName(), WaymarkConstants.DEFAULT_GROUP);

        LOGGER.info("==== Waymark Spring Boot 3 示例启动 ====");
        LOGGER.info("配置监听: namespace={} group={} dataIds={}", namespace, group,
                properties.getConfig().getDataIds());
        LOGGER.info("服务发现: 已自动注册 {} {}:{}", properties.getRegistry().getServiceName(),
                properties.getRegistry().getIp(), properties.getRegistry().getPort());
        LOGGER.info("只订阅、不发布配置：请在配置中心修改 {} 观察热加载。", properties.getConfig().getDataIds());

        // 后台线程周期性查看服务发现结果。
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(TICK_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        reportInstances();
                    } catch (RuntimeException e) {
                        LOGGER.warn("示例执行失败: {}", e.getMessage());
                    }
                }
            }
        }, "waymark-example-runner");
        thread.setDaemon(true);
        thread.start();
    }

    /** 通过服务发现解析器查看自身服务的在线实例，并演示按权重选择一个实例。 */
    private void reportInstances() {
        String service = properties.getRegistry().getServiceName();
        List<Instance> instances = resolver.instances(service);
        LOGGER.info("服务发现: {} 在线实例数={}", service, instances.size());
        for (Instance instance : instances) {
            LOGGER.info("  - {}:{} weight={}", instance.ip, instance.port, instance.weight);
        }
        try {
            LOGGER.info("平滑加权轮询选中实例: {}", resolver.pickURL(service));
        } catch (RuntimeException e) {
            LOGGER.info("暂无可选实例: {}", e.getMessage());
        }
    }

    private static String orDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
