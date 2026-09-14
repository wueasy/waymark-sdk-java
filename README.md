# Waymark Java SDK

> Waymark 注册中心 / 配置中心的 Java 客户端：配置管理、服务注册与发现、SSE 变更订阅，并提供 Spring Boot 2 / 3 Starter 自动装配与配置热加载。

## 模块

| 模块 | artifactId | JDK | 说明 |
|------|-----------|-----|------|
| 核心库 | `com.wueasy.waymark:waymark-java` | 8+ | 客户端与全部 API，无 Spring 依赖 |
| Spring Boot 2 Starter | `com.wueasy.waymark:waymark-spring-boot2-starter` | 8+ | 适配 Spring Boot 2.7.x，自动装配 + 配置热加载 |
| Spring Boot 3 Starter | `com.wueasy.waymark:waymark-spring-boot3-starter` | 17+ | 适配 Spring Boot 3.x，自动装配 + 配置热加载 |
| 示例 | `examples/core`、`examples/spring-boot2`、`examples/spring-boot3` | — | 独立聚合工程 |

- 版本：`0.1.0`
- 核心库仅依赖 `jackson-databind` 2.15.4 与 `slf4j-api` 1.7.36；HTTP 使用 JDK 内置 `HttpURLConnection`，无第三方 HTTP 库。

## 安装

当前未发布到公共仓库，请先在仓库根目录执行本地安装：

```bash
mvn -f waymark-sdk-java/pom.xml clean install -DskipTests
```

随后按需引入：

```xml
<!-- 核心库 -->
<dependency>
    <groupId>com.wueasy.waymark</groupId>
    <artifactId>waymark-java</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Spring Boot 2 项目 -->
<dependency>
    <groupId>com.wueasy.waymark</groupId>
    <artifactId>waymark-spring-boot2-starter</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Spring Boot 3 项目 -->
<dependency>
    <groupId>com.wueasy.waymark</groupId>
    <artifactId>waymark-spring-boot3-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

> Spring Boot 3 项目请用 `spring-boot-dependencies` BOM 统一管理依赖版本，避免核心库传递的 `slf4j-api` 1.7.x 覆盖 Boot 3 所需的 2.0.x 导致 logback 无法绑定。

## 快速开始

```java
import com.wueasy.waymark.*;
import com.wueasy.waymark.model.*;
import com.wueasy.waymark.internal.subscribe.Subscription;

ClientConfig config = ClientConfig.builder()
        .endpoint("http://127.0.0.1:9868")
        .username("admin")
        .password("123456")
        .timeoutMillis(10_000)
        .build();

WaymarkClient client = WaymarkClient.create(config);
try {
    // 发布配置
    PublishConfigRequest req = new PublishConfigRequest();
    req.namespace = WaymarkConstants.DEFAULT_NAMESPACE;
    req.groupName = WaymarkConstants.DEFAULT_GROUP;
    req.dataId = "app.yaml";
    req.content = "server:\n  port: 8080\n";
    req.type = WaymarkConstants.CONFIG_TYPE_YAML;
    client.publishConfig(req);

    // 读取配置
    ConfigItem item = client.getConfig(
            WaymarkConstants.DEFAULT_NAMESPACE, WaymarkConstants.DEFAULT_GROUP, "app.yaml");
    System.out.println(item.md5 + " " + item.content);

    // 注册实例
    InstanceRequest instance = new InstanceRequest()
            .namespace(WaymarkConstants.DEFAULT_NAMESPACE)
            .groupName(WaymarkConstants.DEFAULT_GROUP)
            .serviceName("order-service")
            .ip("127.0.0.1")
            .port(8080)
            .weight(1.0)
            .putMetadata("version", "1.0.0");
    client.registerInstance(instance);

    // 订阅变更（返回可关闭的订阅句柄）
    SubscribeOptions options = new SubscribeOptions();
    options.namespace = WaymarkConstants.DEFAULT_NAMESPACE;
    options.groupName = WaymarkConstants.DEFAULT_GROUP;
    options.dataIds = java.util.Collections.singletonList("app.yaml");
    options.serviceName = "order-service";
    options.handler = ev -> System.out.println("变更: " + ev.eventType + " " + ev.watchKey);
    Subscription subscription = client.watch(options);
    // ...
    subscription.close();
} finally {
    client.close();
}
```

`WaymarkClient` **并发安全，可长期持有**：令牌缺失或失效时自动使用配置的账号密码登录，`getConfig` 会本地缓存并在配置中心不可达时回退缓存。所有异常均为非受检异常（`RuntimeException`），失败时抛出 `WaymarkException` 及其子类。

## 客户端配置（ClientConfig）

通过 `ClientConfig.builder()` 构建：

| Builder 方法 | 类型 | 默认值 | 说明 |
|--------------|------|--------|------|
| `endpoint(String)` | String | 必填 | 服务端地址；多个地址用英文逗号分隔，默认使用第一个，网络层错误时故障转移 |
| `username(String)` | String | 空 | 登录用户名，配置后令牌缺失/过期可自动登录 |
| `password(String)` | String | 空 | 登录密码 |
| `token(String)` | String | 空 | 已有访问令牌，非空时直接使用 |
| `timeoutMillis(int)` | int | `10000` | 单次请求超时（毫秒）；订阅长连接不受此限制 |
| `cacheDir(String)` | String | 系统用户缓存目录下的 `waymark` | 本地配置缓存目录 |
| `disableCache(boolean)` | boolean | `false` | 是否关闭本地配置缓存 |
| `logger(Logger)` | `org.slf4j.Logger` | 名为 `com.wueasy.waymark` 的 logger | 自定义日志器 |

## 认证

| 方法 | 说明 |
|------|------|
| `boolean initStatus()` | 查询系统是否已初始化 |
| `void init(String username, String password, String nickname)` | 初始化首个管理员账号 |
| `LoginResult login()` | 使用配置的账号密码登录并缓存令牌；无凭据时抛 `NoCredentialsException` |
| `LoginResult loginWith(String username, String password)` | 使用指定账号密码登录并缓存令牌 |
| `UserProfile profile()` | 查询当前登录用户资料（含角色与命名空间权限） |

正常使用**无需手动登录**：带认证的请求遇 401 且具备账号密码时会自动重登并重试一次。若既无令牌又无账号密码，抛 `NoCredentialsException`。

## 配置中心

| 方法 | 说明 |
|------|------|
| `ConfigPage listConfigs(ListConfigsOptions options)` | 分页查询配置列表，`pageNum` 从 1 开始 |
| `ConfigItem getConfig(String namespace, String group, String dataId)` | 查询配置详情，成功写入本地缓存；网络不可达时回退缓存 |
| `void publishConfig(PublishConfigRequest request)` | 发布或更新配置，`type` 为空按 `text` 处理 |
| `void deleteConfig(String namespace, String group, String dataId)` | 删除配置 |
| `List<ConfigHistory> configHistory(String namespace, String group, String dataId)` | 查询全部历史版本（内部按 200/页循环合并） |
| `void restoreConfig(String namespace, String group, String dataId, long historyId)` | 将指定历史版本还原为当前配置 |
| `byte[] exportConfigs(ExportOptions options)` | 导出配置为 zip 字节流 |
| `ImportResult importConfigs(String namespace, String group, byte[] zipData)` | 从 zip 字节流导入配置；`group` 非空则全部导入到该分组，否则沿用包内原分组 |

`ExportOptions` 中 `items` 非空则按 items 精确导出，否则按 `namespace` / `groupName` / `dataId` 过滤导出。

## 注册中心

| 方法 | 说明 |
|------|------|
| `RegisterInstanceResult registerInstance(InstanceRequest request)` | 注册实例，已存在则更新并返回 `created=false` |
| `void updateInstance(InstanceRequest request)` | 更新实例属性 |
| `void deregisterInstance(String namespace, String group, String service, String ip, int port)` | 注销实例 |
| `void beat(String namespace, String group, String service, String ip, int port)` | 发送实例心跳 |
| `List<Instance> listInstances(String namespace, String group, String service)` | 查询实例列表，`group`/`service` 为空表示不过滤 |
| `List<ServiceSummary> listServices(String namespace, String group)` | 查询服务概览列表（内部按 200/页循环合并） |

`InstanceRequest` 使用链式 setter；`weight <= 0` 服务端按 1 处理，`healthy` / `ephemeral` 为 `Integer`，传 `null` 时由服务端按默认值（1）处理。

> 临时实例（`ephemeral = 1`）需持续心跳，超过服务端心跳超时（默认 15s）会被淘汰；生产环境建议使用 `RegistryResolver` 的 `self` 自动保活。

## 变更订阅（SSE）

```java
public Subscription watch(SubscribeOptions options)
```

返回的 `Subscription`（位于 `com.wueasy.waymark.internal.subscribe` 包，需单独 `import`）实现 `Closeable`，提供 `isRunning()`、`close()`、`awaitTermination(long timeoutMillis)`。断线后按 `reconnectDelayMillis`（默认 3s）自动重连。

`SubscribeOptions`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `namespace` | String | 空表示默认命名空间 `public` |
| `groupName` | String | 空表示默认分组 `DEFAULT_GROUP` |
| `dataIds` | `List<String>` | 要订阅的配置；`"*"` 表示该分组全部配置；为空表示不订阅配置 |
| `serviceName` | String | 要订阅的服务名；为空表示该分组下全部服务 |
| `handler` | `Consumer<Event>` | 事件回调，在接收线程中**同步**执行，勿做长阻塞 |
| `reconnectDelayMillis` | long | 重连间隔，`<= 0` 用默认 3s |

事件只携带定位信息（`eventType` / `namespace` / `group` / `watchKey` / `md5`），收到后需自行调用 `getConfig` / `listInstances` 拉取最新数据。认证使用 `Authorization: Bearer <token>` 请求头；`NoCredentialsException` 与 403 属不可恢复错误，不重连。

## 快速初始化组件

### ConfigWatcher：配置监听

创建时先为每个 `dataId` 加载并回调一次，随后后台订阅变更，配置更新时自动拉取最新内容并回调。

```java
ConfigWatcherOptions watcherOptions = new ConfigWatcherOptions();
watcherOptions.namespace = WaymarkConstants.DEFAULT_NAMESPACE;
watcherOptions.groupName = WaymarkConstants.DEFAULT_GROUP;
watcherOptions.dataIds = Arrays.asList("app.yaml", "common.yaml");
watcherOptions.onChange = item -> System.out.println("配置更新: " + item.dataId + " " + item.content);
watcherOptions.onError = err -> System.out.println("监听异常: " + err.getMessage());
ConfigWatcher watcher = client.newConfigWatcher(watcherOptions);

ConfigItem latest = watcher.get("app.yaml"); // 内存快照，未命中返回 null
watcher.close();
```

`ConfigWatcherOptions`：`namespace` / `groupName`（空取默认）、`dataIds`（至少一个，否则抛 `WaymarkException`）、`onChange`（必填）、`onError`（可选）、`reconnectDelayMillis`（默认 3s）。

### RegistryResolver：服务发现

将在线实例缓存到内存，按服务名提供**平滑加权轮询（SWRR）**选择；`self` 非空时自动注册自身实例并保持心跳，`close()` 时自动注销。

```java
RegistryResolverOptions resolverOptions = new RegistryResolverOptions();
resolverOptions.namespace = WaymarkConstants.DEFAULT_NAMESPACE;
resolverOptions.groupName = WaymarkConstants.DEFAULT_GROUP;
resolverOptions.self = new InstanceRequest()
        .serviceName("order-service")
        .ip("127.0.0.1")
        .port(8080)
        .weight(1.0)
        .putMetadata("version", "1.0.0");
resolverOptions.onError = err -> System.out.println("发现异常: " + err.getMessage());
RegistryResolver resolver = client.newRegistryResolver(resolverOptions);

SelectedInstance picked = resolver.pick("order-service"); // SWRR 选出一个在线实例
String url = resolver.pickURL("order-service");          // 形如 http://127.0.0.1:8080
List<Instance> instances = resolver.instances("order-service");
resolver.close();
```

`RegistryResolverOptions`：`namespace` / `groupName`（空取默认）、`self`（可选）、`heartbeatIntervalMillis`（默认 5s）、`refreshIntervalMillis`（默认 30s，全量刷新兜底）、`onError`（可选）、`reconnectDelayMillis`（默认 3s）。

行为要点：

- 仅缓存 `healthy == 1` 的实例；权重 `<= 0` 按 1 计算；无在线实例时 `pick` 抛 `WaymarkException`。
- 心跳失败会自动尝试重新注册。
- 同 namespace + group 的配置订阅与实例订阅由 `SubscriptionHub` **复用同一条 SSE 连接**，连接订阅的 dataId 为各注册项的并集，并集变化时自动重建。
- `ConfigWatcher` / `RegistryResolver` / `Subscription` 的 `close()` 均幂等。

## Spring Boot Starter

### 使用方式

引入对应 Starter 后填入配置即可，无需任何代码：

```yaml
waymark:
  endpoint: ${WAYMARK_ENDPOINT:http://127.0.0.1:9868}
  username: ${WAYMARK_USERNAME:admin}
  password: ${WAYMARK_PASSWORD:123456}

  config:
    enabled: true
    namespace: public
    group-name: DEFAULT_GROUP
    data-ids: demo-app.yaml

  registry:
    enabled: true
    namespace: public
    group-name: DEFAULT_GROUP
    service-name: waymark-demo-service
    ip: 127.0.0.1
    port: 8080
    weight: 1.0
    metadata:
      version: 1.0.0

logging:
  level:
    com.wueasy.waymark: DEBUG
```

### 配置项

顶层 `waymark.*`：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `waymark.enabled` | boolean | `true` | 是否启用自动配置 |
| `waymark.endpoint` | String | 无 | 服务端地址，逗号分隔多地址故障转移 |
| `waymark.username` | String | 无 | 登录用户名 |
| `waymark.password` | String | 无 | 登录密码 |
| `waymark.token` | String | 无 | 已有令牌，非空直接用 |
| `waymark.timeout` | Duration | `10s` | 单次请求超时 |
| `waymark.cache-dir` | String | 无 | 本地缓存目录 |
| `waymark.disable-cache` | boolean | `false` | 是否关闭本地缓存 |

`waymark.config.*`（配置监听）：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `waymark.config.enabled` | boolean | `true` | 是否启用配置监听 |
| `waymark.config.namespace` | String | 空 | 空=默认命名空间 |
| `waymark.config.group-name` | String | 空 | 空=默认分组 |
| `waymark.config.data-ids` | `List<String>` | 空 | 监听的 dataId，逗号分隔或列表均可；非空时自动开启监听 |
| `waymark.config.reconnect-delay` | Duration | `3s` | 订阅断线重连间隔 |

`waymark.registry.*`（服务发现）：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `waymark.registry.enabled` | boolean | `false` | 是否启用服务发现 |
| `waymark.registry.namespace` | String | 空 | 空=默认命名空间 |
| `waymark.registry.group-name` | String | 空 | 空=默认分组 |
| `waymark.registry.service-name` | String | 空 | 非空时自动注册自身实例；空则仅发现 |
| `waymark.registry.ip` | String | 空 | 自身 IP |
| `waymark.registry.port` | int | `0` | 自身端口 |
| `waymark.registry.cluster-name` | String | 空 | 空=默认集群 |
| `waymark.registry.weight` | double | `0` | 权重，`<= 0` 服务端按 1 |
| `waymark.registry.heartbeat-interval` | Duration | `5s` | 心跳间隔 |
| `waymark.registry.refresh-interval` | Duration | `30s` | 内存实例全量刷新间隔 |
| `waymark.registry.reconnect-delay` | Duration | `3s` | 订阅重连间隔 |
| `waymark.registry.metadata` | `Map<String,String>` | 空 | 自身实例元数据 |

### 自动装配的 Bean

`WaymarkAutoConfiguration` 在 `waymark.enabled=true`（默认）时生效：

| Bean | 条件 | 说明 |
|------|------|------|
| `WaymarkClient` | `@ConditionalOnMissingBean`，销毁时 `close()` | 由配置构建的客户端 |
| `ConfigWatcher` | `waymark.config.enabled=true` 且 `data-ids` 非空，销毁时 `close()` | 配置监听 + 热加载 |
| `RegistryResolver` | `waymark.registry.enabled=true`，销毁时 `close()` | 服务发现；`service-name` 非空时自动注册自身 |
| `WaymarkConfigRefresher` | `@ConditionalOnMissingBean` | 配置热加载器 |

### 配置热加载

1. `ConfigWatcher` 加载配置并在变更时回调 `WaymarkConfigRefresher.apply(item)`。
2. 配置内容被解析为 Spring `PropertySource` 并插入到 `Environment` 最前面。
3. 对**已创建**的 `@ConfigurationProperties` Bean 逐个用 `Binder` 重新绑定（无需重启）。
4. 发布 `WaymarkConfigChangeEvent`，可通过 `@EventListener` 感知：

```java
@ConfigurationProperties(prefix = "demo")
@Component
public class DemoProperties {
    private String greeting = "not-loaded-yet";
    private boolean featureEnabled;
    // getter / setter
}

@Component
public class DemoConfigChangeListener {
    @EventListener
    public void onConfigChange(WaymarkConfigChangeEvent event) {
        System.out.println("配置变更: " + event.getDataId());
    }
}
```

> 仅 `yaml/yml` 与 `properties` 类型的配置会写入 `Environment` 并触发重绑定；其他类型（text/json/xml）只发布 `WaymarkConfigChangeEvent`，需应用自行处理。启动阶段容器尚未 `active` 时只写属性源，不重绑定、不发事件。

### Spring Boot 2 与 3 的差异

| 维度 | Boot 2 Starter | Boot 3 Starter |
|------|----------------|----------------|
| 自动装配注解 | `@Configuration(proxyBeanMethods = false)` | `@AutoConfiguration` |
| 注册文件 | `META-INF/spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| Spring Boot 版本 | 2.7.12 | 3.3.6 |
| JDK | 8+ | 17+ |

## 异常体系

| 异常 | 说明 |
|------|------|
| `WaymarkException extends RuntimeException` | 基础异常（非受检） |
| `ApiError extends WaymarkException` | 服务端业务错误，含 `getCode()`、`getHttpStatus()`、`isUnauthorized()`、`isForbidden()` |
| `TransportException extends WaymarkException` | 网络传输异常（连接失败/超时/读写失败），表示配置中心不可达，`getConfig` 可安全回退本地缓存 |
| `NoCredentialsException extends WaymarkException` | 未配置用户名密码且无令牌；订阅场景下属不可恢复错误，不重连 |

## 常量与默认值

`WaymarkConstants`（与服务端及 Go SDK 契约一致）：

```java
DEFAULT_NAMESPACE   // "public"
DEFAULT_GROUP       // "DEFAULT_GROUP"
DEFAULT_CLUSTER     // "DEFAULT"
CONFIG_TYPE_TEXT / JSON / YAML / PROPERTIES / XML
EVENT_TYPE_CONFIG   // "CONFIG"
EVENT_TYPE_INSTANCE // "INSTANCE"
CODE_FAIL           // 1001
CODE_UNAUTHORIZED   // 401
CODE_FORBIDDEN      // 403
```

| 项 | 默认值 |
|----|--------|
| 请求超时 | 10s |
| 后台任务单次调用超时 | 5s |
| 订阅重连间隔 | 3s |
| 自身实例心跳间隔 | 5s |
| 实例全量刷新间隔 | 30s |
| 历史/服务分页大小 | 200 |

## 示例

### 纯 Java（examples/core）

```bash
cd waymark-sdk-java/examples/core
mvn exec:java -Dexec.mainClass=com.wueasy.waymark.examples.ConfigDemo     # 配置监听
mvn exec:java -Dexec.mainClass=com.wueasy.waymark.examples.DiscoveryDemo  # 服务发现
mvn exec:java -Dexec.mainClass=com.wueasy.waymark.examples.FullDemo       # 完整流程
mvn exec:java -Dexec.mainClass=com.wueasy.waymark.examples.AllDemo        # 最小骨架
```

参数与环境变量：`-endpoint` / `WAYMARK_ENDPOINT`（默认 `http://127.0.0.1:9868`）、`-username` / `WAYMARK_USERNAME`（默认 `admin`）、`-password` / `WAYMARK_PASSWORD`（默认 `123456`）、`-namespace` / `WAYMARK_NAMESPACE`（默认 `public`）。

### Spring Boot（examples/spring-boot2、examples/spring-boot3）

分别演示自动装配、`@ConfigurationProperties` 热加载与 `WaymarkConfigChangeEvent` 监听，直接运行对应模块的启动类即可（默认端口 8080 / 8081）。

## 注意事项

1. **生命周期**：`WaymarkClient`、`ConfigWatcher`、`RegistryResolver`、`Subscription` 均需在使用完毕后 `close()`；Spring Boot 场景下已通过 `destroyMethod` 自动释放。非 Spring 场景建议在 shutdown hook 中按「解析器 → 监听器 → 订阅 → 注销实例 → 客户端」的顺序关闭。
2. **事件需二次拉取**：`Event` 仅含定位信息，直接用 `watch()` 时需自行拉取最新数据（`ConfigWatcher` 已内置）。
3. **回调勿阻塞**：回调在后台接收线程中同步执行。
4. **故障转移范围**：仅网络层错误（`TransportException`）触发多地址切换，业务错误不切换。
5. **缓存降级范围**：仅 `getConfig` 回退本地缓存，且仅在网络不可达时触发；缓存文件采用「临时文件 + 原子重命名」写入，写失败静默忽略。
6. **心跳约束**：心跳间隔需明显小于服务端 `registry.heartbeat-timeout`（默认 15s）。
7. **传输协议**：仅使用 `java.net.HttpURLConnection`；如需 HTTPS 由部署侧配置。
