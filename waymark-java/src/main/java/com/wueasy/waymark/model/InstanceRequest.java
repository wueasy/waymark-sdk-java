package com.wueasy.waymark.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册或更新实例参数。采用链式 setter 构建：
 *
 * <pre>
 * InstanceRequest req = new InstanceRequest()
 *         .serviceName("order-service")
 *         .ip("127.0.0.1")
 *         .port(8080);
 * </pre>
 */
public class InstanceRequest {

    private String namespace;

    private String groupName;

    private String serviceName;

    private String clusterName;

    private String ip;

    private int port;

    /** 权重，小于等于 0 时服务端按 1 处理。 */
    private double weight;

    /** 健康状态，null 表示由服务端默认（1）。 */
    private Integer healthy;

    /** 是否为临时实例，null 表示由服务端默认（1）。 */
    private Integer ephemeral;

    private Map<String, String> metadata;

    public String getNamespace() {
        return namespace;
    }

    public InstanceRequest namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public String getGroupName() {
        return groupName;
    }

    public InstanceRequest groupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public String getServiceName() {
        return serviceName;
    }

    public InstanceRequest serviceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public String getClusterName() {
        return clusterName;
    }

    public InstanceRequest clusterName(String clusterName) {
        this.clusterName = clusterName;
        return this;
    }

    public String getIp() {
        return ip;
    }

    public InstanceRequest ip(String ip) {
        this.ip = ip;
        return this;
    }

    public int getPort() {
        return port;
    }

    public InstanceRequest port(int port) {
        this.port = port;
        return this;
    }

    public double getWeight() {
        return weight;
    }

    public InstanceRequest weight(double weight) {
        this.weight = weight;
        return this;
    }

    public Integer getHealthy() {
        return healthy;
    }

    public InstanceRequest healthy(Integer healthy) {
        this.healthy = healthy;
        return this;
    }

    public Integer getEphemeral() {
        return ephemeral;
    }

    public InstanceRequest ephemeral(Integer ephemeral) {
        this.ephemeral = ephemeral;
        return this;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public InstanceRequest metadata(Map<String, String> metadata) {
        this.metadata = metadata;
        return this;
    }

    public InstanceRequest putMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new LinkedHashMap<String, String>();
        }
        this.metadata.put(key, value);
        return this;
    }
}
