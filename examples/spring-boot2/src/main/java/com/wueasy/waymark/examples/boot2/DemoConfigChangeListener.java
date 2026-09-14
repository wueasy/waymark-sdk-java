package com.wueasy.waymark.examples.boot2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wueasy.waymark.spring.boot.autoconfigure.WaymarkConfigChangeEvent;

/**
 * 演示配置热加载：监听 {@link WaymarkConfigChangeEvent}。
 *
 * <p>starter 在收到配置变更后会先把配置写入 Spring {@code Environment} 并重新绑定
 * {@code @ConfigurationProperties} Bean，然后发布该事件，因此监听器中读取到的
 * {@link DemoProperties} 已是最新值，无需重启应用。</p>
 */
@Component
public class DemoConfigChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoConfigChangeListener.class);

    private final DemoProperties demoProperties;

    public DemoConfigChangeListener(DemoProperties demoProperties) {
        this.demoProperties = demoProperties;
    }

    /** 配置变更回调。 */
    @EventListener
    public void onConfigChange(WaymarkConfigChangeEvent event) {
        LOGGER.info("收到配置变更事件: dataId={} -> demo.greeting={} demo.feature-enabled={}",
                event.getDataId(), demoProperties.getGreeting(), demoProperties.isFeatureEnabled());
    }
}
