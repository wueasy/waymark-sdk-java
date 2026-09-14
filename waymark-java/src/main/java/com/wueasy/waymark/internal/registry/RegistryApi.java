package com.wueasy.waymark.internal.registry;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.wueasy.waymark.internal.transport.Query;
import com.wueasy.waymark.internal.transport.TransportClient;
import com.wueasy.waymark.internal.util.JsonUtil;
import com.wueasy.waymark.model.Instance;
import com.wueasy.waymark.model.InstanceRequest;
import com.wueasy.waymark.model.RegisterInstanceResult;
import com.wueasy.waymark.model.ServicePage;
import com.wueasy.waymark.model.ServiceSummary;

/**
 * 注册中心的实例注册、心跳与查询接口实现。
 */
public final class RegistryApi {

    private static final String API_INSTANCE = "/api/registry/instance";

    private static final String API_BEAT = "/api/registry/beat";

    private static final String API_INSTANCES = "/api/registry/instances";

    private static final String API_SERVICES = "/api/registry/services";

    /** 服务概览分页拉取的单页大小。 */
    private static final int SERVICE_PAGE_SIZE = 200;

    private RegistryApi() {
    }

    /** 注册实例，实例已存在时更新并返回 created=false。 */
    public static RegisterInstanceResult registerInstance(TransportClient client, InstanceRequest request) {
        JsonNode data = client.send("POST", API_INSTANCE, null, request);
        RegisterInstanceResult result = JsonUtil.convert(data, RegisterInstanceResult.class);
        if (result == null) {
            result = new RegisterInstanceResult();
        }
        client.logger().info("waymark: 注册实例成功 service={} address={}:{} created={}",
                request.getServiceName(), request.getIp(), request.getPort(), result.created);
        return result;
    }

    /** 更新实例属性。 */
    public static void updateInstance(TransportClient client, InstanceRequest request) {
        client.send("PUT", API_INSTANCE, null, request);
        client.logger().debug("waymark: 更新实例成功 service={} address={}:{}",
                request.getServiceName(), request.getIp(), request.getPort());
    }

    /** 注销实例。 */
    public static void deregisterInstance(TransportClient client, String namespace, String group, String service,
            String ip, int port) {
        client.send("DELETE", API_INSTANCE,
                locationQuery(namespace, group, service, ip, port), null);
        client.logger().info("waymark: 注销实例成功 service={} address={}:{}", service, ip, port);
    }

    /** 发送实例心跳。 */
    public static void beat(TransportClient client, String namespace, String group, String service, String ip,
            int port) {
        client.send("PUT", API_BEAT, locationQuery(namespace, group, service, ip, port), null);
        client.logger().debug("waymark: 实例心跳成功 service={} address={}:{}", service, ip, port);
    }

    /** 查询实例列表，group/service 为空表示不过滤。 */
    public static List<Instance> listInstances(TransportClient client, String namespace, String group,
            String service) {
        Query query = new Query();
        query.addIfNotBlank("namespace", namespace);
        query.addIfNotBlank("groupName", group);
        query.addIfNotBlank("serviceName", service);
        JsonNode data = client.send("GET", API_INSTANCES, query, null);
        return JsonUtil.convertList(data, Instance.class);
    }

    /** 查询服务概览列表，group 为空表示不过滤，返回该命名空间下的全部服务。 */
    public static List<ServiceSummary> listServices(TransportClient client, String namespace, String group) {
        // 服务端按页返回且单页有上限，这里循环拉取所有分页后合并为完整列表。
        List<ServiceSummary> result = new ArrayList<ServiceSummary>();
        for (int pageNum = 1; ; pageNum++) {
            Query query = new Query();
            query.addIfNotBlank("namespace", namespace);
            query.addIfNotBlank("groupName", group);
            query.set("pageNum", String.valueOf(pageNum));
            query.set("pageSize", String.valueOf(SERVICE_PAGE_SIZE));
            JsonNode data = client.send("GET", API_SERVICES, query, null);
            ServicePage page = JsonUtil.convert(data, ServicePage.class);
            if (page == null || page.list == null || page.list.isEmpty()) {
                break;
            }
            result.addAll(page.list);
            if (result.size() >= page.total) {
                break;
            }
        }
        return result;
    }

    private static Query locationQuery(String namespace, String group, String service, String ip, int port) {
        Query query = new Query();
        query.addIfNotBlank("namespace", namespace);
        query.addIfNotBlank("groupName", group);
        query.set("serviceName", service);
        query.set("ip", ip);
        query.set("port", String.valueOf(port));
        return query;
    }
}
