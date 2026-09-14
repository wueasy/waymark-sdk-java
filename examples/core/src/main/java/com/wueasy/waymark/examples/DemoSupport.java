package com.wueasy.waymark.examples;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.slf4j.LoggerFactory;

import com.wueasy.waymark.ClientConfig;
import com.wueasy.waymark.WaymarkClient;
import com.wueasy.waymark.WaymarkConstants;

/**
 * 示例程序共用辅助：命令行/环境变量解析、客户端创建、输出与退出辅助，
 * 让各启动入口专注于演示快速初始化组件的用法。
 */
public final class DemoSupport {

    /** 统一使用 UTF-8 输出，避免 Windows 控制台（chcp 65001）中文乱码。 */
    public static final PrintStream OUT = utf8Stream();

    /** 示例默认分组。 */
    public static final String DEMO_GROUP = WaymarkConstants.DEFAULT_GROUP;

    public static final String DEMO_DATA_ID = "demo.yaml";

    public static final String DEMO_APP_DATA_ID = "demo-app.yaml";

    public static final String DEMO_SERVICE = "waymark-demo-service";

    public static final String DEMO_IP = "127.0.0.1";

    public static final int DEMO_PORT = 18080;

    /** 快速初始化示例使用的自身实例，与手动示例区分。 */
    public static final String DEMO_QUICK_SERVICE = "waymark-demo-quick-service";

    public static final int DEMO_QUICK_PORT = 18081;

    /** 实例心跳间隔（毫秒），需小于服务端心跳超时时间（默认 15s）。 */
    public static final long DEMO_HEARTBEAT_INTERVAL_MILLIS = 5000L;

    private DemoSupport() {
    }

    public static void println(String message) {
        OUT.println(message);
    }

    public static void printf(String format, Object... args) {
        OUT.printf(format, args);
    }

    private static PrintStream utf8Stream() {
        try {
            return new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return System.out;
        }
    }

    /** 读取系统属性/环境变量，均为空时返回默认值。 */
    public static String env(String key, String def) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            value = System.getenv(key);
        }
        return (value == null || value.isEmpty()) ? def : value;
    }

    /** 按逗号拆分字符串，去除每项的空白并忽略空项。 */
    public static List<String> splitList(String value) {
        List<String> out = new ArrayList<String>();
        if (value == null) {
            return out;
        }
        for (String part : value.split(",")) {
            String item = part.trim();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    /** 为多行文本的每一行添加前缀，便于对齐输出。 */
    public static String indent(String text, String prefix) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        int end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == '\n') {
            end--;
        }
        String[] lines = normalized.substring(0, end).split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(prefix).append(line).append('\n');
        }
        return builder.toString();
    }

    /** 注册退出清理任务：收到 Ctrl+C/SIGTERM 时先执行清理，再打印退出提示。 */
    public static void onShutdown(final Runnable onShutdown) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                if (onShutdown != null) {
                    try {
                        onShutdown.run();
                    } catch (RuntimeException e) {
                        printf("[错误] 退出清理失败: %s%n", e.getMessage());
                    }
                }
                println("");
                println("== 退出 ==");
            }
        }, "waymark-example-shutdown"));
    }

    /** 常驻运行，直到收到 Ctrl+C/SIGTERM；退出时先执行清理任务。 */
    public static void awaitShutdown(Runnable onShutdown) throws InterruptedException {
        onShutdown(onShutdown);
        new CountDownLatch(1).await();
    }

    /**
     * 命令行参数解析：支持 -key value、-key=value 与布尔开关 -key。
     */
    public static final class Args {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        private Args() {
        }

        public static Args parse(String[] args) {
            Args parsed = new Args();
            if (args == null) {
                return parsed;
            }
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg == null || arg.isEmpty() || arg.charAt(0) != '-') {
                    continue;
                }
                String key = arg.substring(1);
                String value;
                int eq = key.indexOf('=');
                if (eq >= 0) {
                    value = key.substring(eq + 1);
                    key = key.substring(0, eq);
                } else if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    value = args[++i];
                } else {
                    value = "true";
                }
                parsed.values.put(key, value);
            }
            return parsed;
        }

        public String get(String key, String def) {
            String value = values.get(key);
            return (value == null || value.isEmpty()) ? def : value;
        }

        public int getInt(String key, int def) {
            String value = values.get(key);
            if (value == null || value.isEmpty()) {
                return def;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        public double getDouble(String key, double def) {
            String value = values.get(key);
            if (value == null || value.isEmpty()) {
                return def;
            }
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        public boolean getBool(String key, boolean def) {
            String value = values.get(key);
            if (value == null || value.isEmpty()) {
                return def;
            }
            return "true".equalsIgnoreCase(value) || "1".equals(value);
        }
    }

    /**
     * 示例共用的服务端连接参数（-endpoint/-username/-password/-namespace/-log），
     * 未显式指定时回退到环境变量与默认值。endpoint 可填多个地址（英文逗号分隔），支持故障转移。
     */
    public static final class Options {

        /** 服务端地址，多个地址用英文逗号分隔。 */
        public String endpoint = env("WAYMARK_ENDPOINT", "http://127.0.0.1:9868");

        public String username = env("WAYMARK_USERNAME", "admin");

        public String password = env("WAYMARK_PASSWORD", "123456");

        public String namespace = env("WAYMARK_NAMESPACE", WaymarkConstants.DEFAULT_NAMESPACE);

        /** 是否输出 SDK 调试日志。 */
        public boolean log = true;

        public static Options bind(Args args) {
            Options options = new Options();
            options.endpoint = args.get("endpoint", options.endpoint);
            options.username = args.get("username", options.username);
            options.password = args.get("password", options.password);
            options.namespace = args.get("namespace", options.namespace);
            options.log = args.getBool("log", options.log);
            return options;
        }

        /**
         * 按参数创建客户端。SDK 会在首次请求时自动登录，并在令牌过期（401）时自动重新登录。
         * 通过 {@code logger(...)} 传入自定义日志记录器，SDK 内部的请求、订阅、调度日志都走该记录器。
         */
        public WaymarkClient newClient() {
            // 需在 logback 初始化前设置，控制 logback.xml 中 waymark-example 记录器的级别。
            System.setProperty("waymark.log.level", log ? "DEBUG" : "WARN");
            return WaymarkClient.create(ClientConfig.builder()
                    .endpoint(endpoint)
                    .username(username)
                    .password(password)
                    .timeoutMillis((int) WaymarkConstants.DEFAULT_TIMEOUT_MILLIS)
                    .logger(LoggerFactory.getLogger("waymark-example"))
                    .build());
        }
    }
}
