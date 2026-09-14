package com.wueasy.waymark.spring.boot.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 配置监听装配条件：{@code waymark.config.enabled=true} 且 {@code waymark.config.data-ids} 非空。
 * 使用 Binder 读取配置，兼容 yaml 列表与逗号分隔两种写法。
 */
final class WaymarkConfigWatchCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        WaymarkProperties properties = bind(context);
        if (!properties.getConfig().isEnabled()) {
            return ConditionOutcome.noMatch("waymark.config.enabled=false");
        }
        if (properties.getConfig().getDataIds() == null || properties.getConfig().getDataIds().isEmpty()) {
            return ConditionOutcome.noMatch("waymark.config.data-ids 未配置，跳过配置监听");
        }
        return ConditionOutcome.match();
    }

    private static WaymarkProperties bind(ConditionContext context) {
        Binder binder = Binder.get(context.getEnvironment());
        return binder.bind("waymark", Bindable.of(WaymarkProperties.class)).orElseGet(WaymarkProperties::new);
    }
}
