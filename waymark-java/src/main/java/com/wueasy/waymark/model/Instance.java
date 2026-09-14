package com.wueasy.waymark.model;

import java.util.Map;

/**
 * 服务实例。
 */
public class Instance {

    public long id;

    public String namespace;

    public String groupName;

    public String serviceName;

    public String clusterName;

    public String ip;

    public int port;

    /** 权重，小于等于 0 时按 1 处理。 */
    public double weight;

    /** 是否健康：1 健康，0 不健康。 */
    public int healthy;

    /** 是否为临时实例：1 是，0 否。 */
    public int ephemeral;

    public Map<String, String> metadata;

    public long lastHeartbeat;

    public long createTime;

    public long updateTime;
}
