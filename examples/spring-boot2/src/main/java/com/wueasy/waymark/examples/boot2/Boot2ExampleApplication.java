package com.wueasy.waymark.examples.boot2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Waymark Spring Boot 2 示例应用（JDK 1.8）。
 *
 * <p>只需引入 {@code waymark-spring-boot2-starter} 并在 application.yml 中填写 {@code waymark.*}
 * 配置，即自动完成：配置加载与热加载、自身实例自动注册与心跳、在线实例缓存与服务发现，
 * 无需任何配置类。</p>
 *
 * <p>示例只订阅、不发布配置：运行前请先在配置中心创建 {@code demo-app.yaml}，
 * 之后在配置中心修改该配置即可观察 {@code demo.greeting} 的热加载。</p>
 */
@SpringBootApplication
public class Boot2ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(Boot2ExampleApplication.class, args);
    }
}
