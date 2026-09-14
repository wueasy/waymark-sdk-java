package com.wueasy.waymark.model;

import java.util.List;
import java.util.function.Consumer;

/**
 * 订阅参数。
 */
public class SubscribeOptions {

    /** 命名空间，为空时使用默认命名空间。 */
    public String namespace;

    /** 分组，为空时使用默认分组。 */
    public String groupName;

    /** 订阅的配置文件名（dataId）列表，传 "*" 表示订阅该分组下全部配置变更，为空表示不订阅配置。 */
    public List<String> dataIds;

    /** 订阅实例变更时使用；为空表示订阅该分组下全部服务变更。 */
    public String serviceName;

    /** 事件回调，收到变更事件时同步调用。 */
    public Consumer<Event> handler;

    /** 断线重连间隔（毫秒），小于等于 0 时使用默认值 3s。 */
    public long reconnectDelayMillis;
}
