package com.wueasy.waymark.examples.boot3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 演示配置热加载：对应配置中心 {@code demo-app.yaml} 中的 {@code demo.*} 配置。
 * Waymark 监听到配置变更后会写入 Spring Environment 并重新绑定本 Bean，无需重启应用。
 */
@Component
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    /** 对应 demo.greeting。 */
    private String greeting = "not-loaded-yet";

    /** 对应 demo.feature-enabled。 */
    private boolean featureEnabled;

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public boolean isFeatureEnabled() {
        return featureEnabled;
    }

    public void setFeatureEnabled(boolean featureEnabled) {
        this.featureEnabled = featureEnabled;
    }
}
