package com.wueasy.waymark.quickstart;

import com.wueasy.waymark.model.InstanceRequest;

import java.util.function.Consumer;

/**
 * 服务发现解析器初始化参数。
 */
public class RegistryResolverOptions {

    /** 命名空间，为空时使用默认命名空间。 */
    public String namespace;

    /** 分组，为空时使用默认分组。 */
    public String groupName;

    /** 自身实例信息；非空时自动注册并保持心跳，close 时自动注销。 */
    public InstanceRequest self;

    /** 自身实例心跳间隔（毫秒），小于等于 0 时使用默认值 5s。 */
    public long heartbeatIntervalMillis;

    /** 内存实例缓存的全量刷新间隔（毫秒），小于等于 0 时使用默认值 30s。 */
    public long refreshIntervalMillis;

    /** 错误回调，可选。 */
    public Consumer<Throwable> onError;

    /** 订阅断线重连间隔（毫秒），小于等于 0 时使用默认值 3s。 */
    public long reconnectDelayMillis;
}
