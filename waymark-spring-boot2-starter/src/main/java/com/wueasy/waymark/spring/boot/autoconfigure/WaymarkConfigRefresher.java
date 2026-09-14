package com.wueasy.waymark.spring.boot.autoconfigure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.wueasy.waymark.model.ConfigItem;

/**
 * 配置热加载：将监听到的配置写入 Spring {@code Environment}，并重新绑定已创建的
 * {@code @ConfigurationProperties} Bean，随后发布 {@link WaymarkConfigChangeEvent} 供应用自定义处理。
 * 支持 yaml/yml 与 properties 两种格式，其余格式仅发布变更事件。
 */
public class WaymarkConfigRefresher {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaymarkConfigRefresher.class);

    private final ConfigurableApplicationContext context;

    private final ConfigurableEnvironment environment;

    public WaymarkConfigRefresher(ConfigurableApplicationContext context) {
        this.context = context;
        this.environment = context.getEnvironment();
    }

    /** 应用一份最新配置：刷新属性源、重新绑定配置属性并发布变更事件。 */
    public void apply(ConfigItem item) {
        if (item == null || item.dataId == null || item.dataId.trim().isEmpty()) {
            return;
        }
        String name = item.dataId.trim();
        List<PropertySource<?>> sources = load(name, item);
        if (!sources.isEmpty()) {
            applyPropertySources(name, sources);
        }
        // 容器尚未就绪时（启动阶段初始化加载）只写入属性源，不触发重绑定与事件。
        if (!context.isActive()) {
            return;
        }
        if (!sources.isEmpty()) {
            rebind();
        }
        context.publishEvent(new WaymarkConfigChangeEvent(this, item));
        LOGGER.info("waymark: 配置 {} 已热加载", name);
    }

    /** 将配置的属性源放到最前，保证同名配置的最新内容优先生效。 */
    private void applyPropertySources(String name, List<PropertySource<?>> sources) {
        MutablePropertySources propertySources = environment.getPropertySources();
        removeExisting(propertySources, name);
        // 逆序插入，保证多文档 yaml 的属性源先后顺序与文件内一致。
        for (int i = sources.size() - 1; i >= 0; i--) {
            propertySources.addFirst(sources.get(i));
        }
    }

    private static void removeExisting(MutablePropertySources propertySources, String name) {
        List<String> stale = new ArrayList<String>();
        for (PropertySource<?> source : propertySources) {
            String sourceName = source.getName();
            if (sourceName.equals(name) || sourceName.startsWith(name + "-")) {
                stale.add(sourceName);
            }
        }
        for (String sourceName : stale) {
            propertySources.remove(sourceName);
        }
    }

    /** 重新绑定已创建的 {@code @ConfigurationProperties} Bean，使最新配置立即生效。 */
    private void rebind() {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        Binder binder = Binder.get(environment);
        String[] beanNames = context.getBeanNamesForAnnotation(ConfigurationProperties.class);
        for (String beanName : beanNames) {
            // 仅重绑定已创建的 Bean，避免热加载过程意外触发 Bean 初始化。
            if (!beanFactory.containsSingleton(beanName)) {
                continue;
            }
            Class<?> beanType = context.getType(beanName);
            if (beanType == null) {
                continue;
            }
            ConfigurationProperties annotation =
                    AnnotatedElementUtils.findMergedAnnotation(beanType, ConfigurationProperties.class);
            if (annotation == null) {
                continue;
            }
            String prefix = annotation.prefix();
            if (prefix.isEmpty()) {
                prefix = annotation.value();
            }
            if (prefix.isEmpty()) {
                continue;
            }
            try {
                Object bean = context.getBean(beanName);
                binder.bind(prefix, Bindable.ofInstance(bean));
            } catch (RuntimeException e) {
                LOGGER.warn("waymark: 重新绑定配置 {} 失败: {}", prefix, e.getMessage());
            }
        }
    }

    private static List<PropertySource<?>> load(String name, ConfigItem item) {
        PropertySourceLoader loader = resolveLoader(name, item);
        if (loader == null) {
            return Collections.emptyList();
        }
        byte[] data = item.contentOrEmpty().getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(data, name);
        try {
            List<PropertySource<?>> sources = loader.load(name, resource);
            return sources == null ? Collections.<PropertySource<?>>emptyList() : sources;
        } catch (Exception e) {
            LOGGER.warn("waymark: 解析配置 {} 失败，跳过 Environment 刷新: {}", name, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static PropertySourceLoader resolveLoader(String name, ConfigItem item) {
        String type = item.type == null ? "" : item.type.trim().toLowerCase();
        String lowerName = name.toLowerCase();
        if ("yaml".equals(type) || "yml".equals(type) || lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
            return new YamlPropertySourceLoader();
        }
        if ("properties".equals(type) || lowerName.endsWith(".properties")) {
            return new PropertiesPropertySourceLoader();
        }
        return null;
    }
}
