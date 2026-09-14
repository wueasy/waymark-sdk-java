package com.wueasy.waymark.quickstart;

import com.wueasy.waymark.model.Instance;

/**
 * 负载均衡选中的实例。
 */
public class SelectedInstance {

    /** 实例信息。 */
    public final Instance instance;

    /** 实例访问链接，形如 http://127.0.0.1:8080。 */
    public final String url;

    /** 命中实例的生效权重，即调度实际使用的权重（权重非正时按 1 处理）。 */
    public final double currentWeight;

    public SelectedInstance(Instance instance, String url, double currentWeight) {
        this.instance = instance;
        this.url = url;
        this.currentWeight = currentWeight;
    }
}
