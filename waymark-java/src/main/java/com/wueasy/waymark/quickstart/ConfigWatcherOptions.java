package com.wueasy.waymark.quickstart;

import java.util.List;
import java.util.function.Consumer;

import com.wueasy.waymark.model.ConfigItem;

/**
 * 配置监听初始化参数。
 */
public class ConfigWatcherOptions {

    /** 命名空间，为空时使用默认命名空间。 */
    public String namespace;

    /** 分组，为空时使用默认分组。 */
    public String groupName;

    /** 监听的配置文件名（dataId）列表，至少指定一个。 */
    public List<String> dataIds;

    /** 配置回调：初始化时每个 dataId 回调一次当前配置，之后配置变更时回调最新配置。 */
    public Consumer<ConfigItem> onChange;

    /** 错误回调，可选；用于上报初始化之后的监听与拉取错误。 */
    public Consumer<Throwable> onError;

    /** 订阅断线重连间隔（毫秒），小于等于 0 时使用默认值 3s。 */
    public long reconnectDelayMillis;
}
