package com.wueasy.waymark;

/**
 * Waymark SDK 常量定义，与 Go SDK 及服务端契约保持一致。
 */
public final class WaymarkConstants {

    /** 默认命名空间。 */
    public static final String DEFAULT_NAMESPACE = "public";

    /** 默认分组。 */
    public static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    /** 默认集群。 */
    public static final String DEFAULT_CLUSTER = "DEFAULT";

    /** 配置类型：纯文本。 */
    public static final String CONFIG_TYPE_TEXT = "text";

    /** 配置类型：JSON。 */
    public static final String CONFIG_TYPE_JSON = "json";

    /** 配置类型：YAML。 */
    public static final String CONFIG_TYPE_YAML = "yaml";

    /** 配置类型：Properties。 */
    public static final String CONFIG_TYPE_PROPERTIES = "properties";

    /** 配置类型：XML。 */
    public static final String CONFIG_TYPE_XML = "xml";

    /** 订阅事件类型：配置变更。 */
    public static final String EVENT_TYPE_CONFIG = "CONFIG";

    /** 订阅事件类型：实例变更。 */
    public static final String EVENT_TYPE_INSTANCE = "INSTANCE";

    /** 服务端统一返回码：业务失败。 */
    public static final int CODE_FAIL = 1001;

    /** 服务端统一返回码：未认证。 */
    public static final int CODE_UNAUTHORIZED = 401;

    /** 服务端统一返回码：无权限。 */
    public static final int CODE_FORBIDDEN = 403;

    /** 单次请求默认超时时间（毫秒）。 */
    public static final int DEFAULT_TIMEOUT_MILLIS = 10_000;

    /** 后台任务单次调用默认超时时间（毫秒）。 */
    public static final int DEFAULT_CALL_TIMEOUT_MILLIS = 5_000;

    /** 自身实例默认心跳间隔（毫秒）。 */
    public static final long DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 5_000L;

    /** 内存实例缓存默认全量刷新间隔（毫秒）。 */
    public static final long DEFAULT_REFRESH_INTERVAL_MILLIS = 30_000L;

    /** 订阅断线默认重连间隔（毫秒）。 */
    public static final long DEFAULT_RECONNECT_DELAY_MILLIS = 3_000L;

    private WaymarkConstants() {
    }
}
