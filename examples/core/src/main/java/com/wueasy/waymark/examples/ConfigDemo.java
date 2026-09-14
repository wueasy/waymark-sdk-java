package com.wueasy.waymark.examples;

import java.util.List;

import com.wueasy.waymark.WaymarkClient;
import com.wueasy.waymark.model.ConfigItem;
import com.wueasy.waymark.quickstart.ConfigWatcher;
import com.wueasy.waymark.quickstart.ConfigWatcherOptions;

/**
 * 配置监听示例（快速初始化 ConfigWatcher）：
 * 一次初始化即完成「加载当前配置 + 订阅变更 + 回调」，只需提供 onChange 回调，
 * 无需手动调用 getConfig 与 watch。
 *
 * <p>运行：
 *
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.wueasy.waymark.examples.ConfigDemo
 * </pre>
 *
 * 只订阅、不发布配置：在配置中心手动修改配置即可看到变更回调。
 */
public final class ConfigDemo {

    public static void main(String[] args) throws Exception {
        DemoSupport.Args parsed = DemoSupport.Args.parse(args);
        final DemoSupport.Options opts = DemoSupport.Options.bind(parsed);
        String group = parsed.get("group", DemoSupport.env("WAYMARK_GROUP", DemoSupport.DEMO_GROUP));
        List<String> dataIds = DemoSupport.splitList(parsed.get(
                "dataIds", DemoSupport.DEMO_DATA_ID + "," + DemoSupport.DEMO_APP_DATA_ID));

        if (dataIds.isEmpty()) {
            DemoSupport.println("dataIds 不能为空");
            return;
        }

        final WaymarkClient client = opts.newClient();
        DemoSupport.printf("== 创建客户端成功: endpoint=%s namespace=%s ==%n", opts.endpoint, opts.namespace);

        // 快速初始化：一次创建即完成加载 + 订阅，变更时自动回调最新配置。
        ConfigWatcherOptions watcherOptions = new ConfigWatcherOptions();
        watcherOptions.namespace = opts.namespace;
        watcherOptions.groupName = group;
        watcherOptions.dataIds = dataIds;
        watcherOptions.onChange = new java.util.function.Consumer<ConfigItem>() {
            @Override
            public void accept(ConfigItem item) {
                DemoSupport.printf("%n[回调] 配置已就绪 dataId=%s type=%s md5=%s%n%s",
                        item.dataId, item.type, item.md5, DemoSupport.indent(item.content, "    "));
            }
        };
        watcherOptions.onError = new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                DemoSupport.printf("[错误] 配置监听: %s%n", error.getMessage());
            }
        };
        final ConfigWatcher watcher = client.newConfigWatcher(watcherOptions);

        // 读取监听器内存中缓存的最新配置。
        for (String dataId : dataIds) {
            ConfigItem cached = watcher.get(dataId);
            if (cached != null) {
                DemoSupport.printf("[缓存] dataId=%s md5=%s%n", cached.dataId, cached.md5);
            }
        }

        DemoSupport.println("");
        DemoSupport.println("配置监听运行中（修改配置即可看到回调）。按 Ctrl+C 退出。");

        DemoSupport.awaitShutdown(new Runnable() {
            @Override
            public void run() {
                watcher.close();
                client.close();
            }
        });
    }

    private ConfigDemo() {
    }
}
