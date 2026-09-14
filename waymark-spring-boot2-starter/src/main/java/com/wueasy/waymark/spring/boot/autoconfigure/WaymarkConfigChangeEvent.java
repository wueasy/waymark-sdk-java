package com.wueasy.waymark.spring.boot.autoconfigure;

import org.springframework.context.ApplicationEvent;

import com.wueasy.waymark.model.ConfigItem;

/**
 * 配置发生变更时发布的事件，携带变更后最新的配置项，便于应用自定义热加载逻辑。
 * 可通过 {@code @EventListener} 监听。
 */
public class WaymarkConfigChangeEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final transient ConfigItem configItem;

    public WaymarkConfigChangeEvent(Object source, ConfigItem configItem) {
        super(source);
        this.configItem = configItem;
    }

    /** 变更后的最新配置项。 */
    public ConfigItem getConfigItem() {
        return configItem;
    }

    /** 变更配置的 dataId。 */
    public String getDataId() {
        return configItem == null ? null : configItem.dataId;
    }
}
