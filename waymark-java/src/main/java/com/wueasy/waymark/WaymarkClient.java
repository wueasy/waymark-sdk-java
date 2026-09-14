package com.wueasy.waymark;

import java.util.List;

import org.slf4j.Logger;

import com.wueasy.waymark.internal.config.ConfigApi;
import com.wueasy.waymark.internal.registry.RegistryApi;
import com.wueasy.waymark.internal.subscribe.Subscription;
import com.wueasy.waymark.internal.transport.TransportClient;
import com.wueasy.waymark.model.ConfigHistory;
import com.wueasy.waymark.model.ConfigItem;
import com.wueasy.waymark.model.ConfigPage;
import com.wueasy.waymark.model.ExportOptions;
import com.wueasy.waymark.model.ImportResult;
import com.wueasy.waymark.model.Instance;
import com.wueasy.waymark.model.InstanceRequest;
import com.wueasy.waymark.model.ListConfigsOptions;
import com.wueasy.waymark.model.LoginResult;
import com.wueasy.waymark.model.PublishConfigRequest;
import com.wueasy.waymark.model.RegisterInstanceResult;
import com.wueasy.waymark.model.ServiceSummary;
import com.wueasy.waymark.model.SubscribeOptions;
import com.wueasy.waymark.model.UserProfile;
import com.wueasy.waymark.quickstart.ConfigWatcher;
import com.wueasy.waymark.quickstart.ConfigWatcherOptions;
import com.wueasy.waymark.quickstart.RegistryResolver;
import com.wueasy.waymark.quickstart.RegistryResolverOptions;

/**
 * Waymark 配置中心与注册中心的 Java 客户端。并发安全，可长期持有。
 *
 * <pre>
 * WaymarkClient client = WaymarkClient.create(ClientConfig.builder()
 *         .endpoint("http://127.0.0.1:9868")
 *         .username("admin")
 *         .password("admin")
 *         .build());
 * ConfigItem item = client.getConfig(WaymarkConstants.DEFAULT_NAMESPACE,
 *         WaymarkConstants.DEFAULT_GROUP, "app.yaml");
 * </pre>
 *
 * 令牌缺失或过期时会自动使用配置的账号密码登录；{@link #getConfig} 会在本地缓存一份配置快照，
 * 配置中心不可用时自动回退到本地缓存。使用 {@link #close()} 释放长连接与后台任务。
 *
 * endpoint 可配置多个服务端地址（英文逗号分隔）：默认使用第一个，遇到网络层错误时自动切换到下一个（故障转移）。
 */
public final class WaymarkClient {

    private final TransportClient core;

    private WaymarkClient(TransportClient core) {
        this.core = core;
    }

    /** 创建客户端。 */
    public static WaymarkClient create(ClientConfig config) {
        return new WaymarkClient(new TransportClient(config));
    }

    /** 当前使用的服务端地址（已去除尾部斜杠）。 */
    public String endpoint() {
        return core.endpoint();
    }

    /** 全部已配置的服务端地址。 */
    public List<String> endpoints() {
        return core.endpoints();
    }

    /** 本地配置缓存目录，为空表示未启用缓存。 */
    public String cacheDir() {
        return core.cacheDir();
    }

    /** 日志记录器。 */
    public Logger logger() {
        return core.logger();
    }

    /** 设置访问令牌。 */
    public void setToken(String token) {
        core.setToken(token);
    }

    /** 返回当前访问令牌。 */
    public String token() {
        return core.token();
    }

    // ==================== 鉴权 ====================

    /** 查询系统是否已初始化。 */
    public boolean initStatus() {
        return core.initStatus();
    }

    /** 初始化首个管理员账号。 */
    public void init(String username, String password, String nickname) {
        core.init(username, password, nickname);
    }

    /** 使用客户端配置的账号密码登录并缓存令牌。 */
    public LoginResult login() {
        return core.login();
    }

    /** 使用指定账号密码登录并缓存令牌。 */
    public LoginResult loginWith(String username, String password) {
        return core.loginWith(username, password);
    }

    /** 查询当前登录用户资料。 */
    public UserProfile profile() {
        return core.profile();
    }

    // ==================== 配置中心 ====================

    /** 分页查询配置列表。 */
    public ConfigPage listConfigs(ListConfigsOptions options) {
        return ConfigApi.listConfigs(core, options);
    }

    /**
     * 查询配置详情。读取成功时会写入本地缓存；配置中心不可用（网络层错误）且本地存在该配置的
     * 缓存时，返回缓存内容。
     */
    public ConfigItem getConfig(String namespace, String group, String dataId) {
        return ConfigApi.getConfig(core, namespace, group, dataId);
    }

    /** 发布或更新配置。 */
    public void publishConfig(PublishConfigRequest request) {
        ConfigApi.publishConfig(core, request);
    }

    /** 删除配置。 */
    public void deleteConfig(String namespace, String group, String dataId) {
        ConfigApi.deleteConfig(core, namespace, group, dataId);
    }

    /** 查询配置历史版本。 */
    public List<ConfigHistory> configHistory(String namespace, String group, String dataId) {
        return ConfigApi.configHistory(core, namespace, group, dataId);
    }

    /** 将指定历史版本还原为当前配置。 */
    public void restoreConfig(String namespace, String group, String dataId, long historyId) {
        ConfigApi.restoreConfig(core, namespace, group, dataId, historyId);
    }

    /** 导出配置为 zip 字节流。 */
    public byte[] exportConfigs(ExportOptions options) {
        return ConfigApi.exportConfigs(core, options);
    }

    /**
     * 从 zip 字节流导入配置。
     * group 非空时全部导入到该分组，否则沿用压缩包中记录的原分组。
     */
    public ImportResult importConfigs(String namespace, String group, byte[] zipData) {
        return ConfigApi.importConfigs(core, namespace, group, zipData);
    }

    // ==================== 注册中心 ====================

    /** 注册实例，实例已存在时更新并返回 created=false。 */
    public RegisterInstanceResult registerInstance(InstanceRequest request) {
        return RegistryApi.registerInstance(core, request);
    }

    /** 更新实例属性。 */
    public void updateInstance(InstanceRequest request) {
        RegistryApi.updateInstance(core, request);
    }

    /** 注销实例。 */
    public void deregisterInstance(String namespace, String group, String service, String ip, int port) {
        RegistryApi.deregisterInstance(core, namespace, group, service, ip, port);
    }

    /** 发送实例心跳。 */
    public void beat(String namespace, String group, String service, String ip, int port) {
        RegistryApi.beat(core, namespace, group, service, ip, port);
    }

    /** 查询实例列表，group/service 为空表示不过滤。 */
    public List<Instance> listInstances(String namespace, String group, String service) {
        return RegistryApi.listInstances(core, namespace, group, service);
    }

    /** 查询服务概览列表，group 为空表示不过滤，返回该命名空间下的全部服务。 */
    public List<ServiceSummary> listServices(String namespace, String group) {
        return RegistryApi.listServices(core, namespace, group);
    }

    // ==================== 订阅与快速初始化 ====================

    /**
     * 建立一条订阅连接，可同时订阅配置与实例变更；连接断开后会自动重连。
     * 变更事件在后台线程同步回调 {@link SubscribeOptions#handler}，使用返回对象的 close 释放。
     */
    public Subscription watch(SubscribeOptions options) {
        return Subscription.start(core, options);
    }

    /**
     * 创建配置监听器：先为每个 dataId 加载一次当前配置并回调，随后在后台订阅配置变更，
     * 变更时自动拉取最新配置并回调。
     */
    public ConfigWatcher newConfigWatcher(ConfigWatcherOptions options) {
        return ConfigWatcher.create(core, options);
    }

    /**
     * 创建服务发现解析器：拉取一次在线实例并缓存到内存，随后订阅实例变更并定时全量刷新；
     * self 非空时自动注册并保持心跳。
     */
    public RegistryResolver newRegistryResolver(RegistryResolverOptions options) {
        return RegistryResolver.create(core, options);
    }

    /** 关闭客户端并断开所有进行中的长连接。 */
    public void close() {
        core.close();
    }
}
