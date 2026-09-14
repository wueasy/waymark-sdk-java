package com.wueasy.waymark.internal.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.wueasy.waymark.ApiError;
import com.wueasy.waymark.TransportException;
import com.wueasy.waymark.WaymarkException;
import com.wueasy.waymark.internal.cache.ConfigCache;
import com.wueasy.waymark.internal.transport.HttpResponse;
import com.wueasy.waymark.internal.transport.Query;
import com.wueasy.waymark.internal.transport.ResultVo;
import com.wueasy.waymark.internal.transport.TransportClient;
import com.wueasy.waymark.internal.util.JsonUtil;
import com.wueasy.waymark.model.ConfigHistory;
import com.wueasy.waymark.model.ConfigItem;
import com.wueasy.waymark.model.ConfigPage;
import com.wueasy.waymark.model.ExportItem;
import com.wueasy.waymark.model.ExportOptions;
import com.wueasy.waymark.model.ImportResult;
import com.wueasy.waymark.model.ListConfigsOptions;
import com.wueasy.waymark.model.PublishConfigRequest;

/**
 * 配置中心的读写、历史、导入导出等接口实现。
 */
public final class ConfigApi {

    private static final String API_CONFIGS = "/api/configs";

    private static final String API_CONFIG_DETAIL = "/api/configs/detail";

    private static final String API_CONFIG_HISTORY = "/api/configs/history";

    private static final String API_CONFIG_RESTORE = "/api/configs/restore";

    private static final String API_CONFIG_EXPORT = "/api/configs/export";

    private static final String API_CONFIG_IMPORT = "/api/configs/import";

    /** 配置历史分页拉取的单页大小。 */
    private static final int CONFIG_HISTORY_PAGE_SIZE = 200;

    private ConfigApi() {
    }

    /** 分页查询配置列表。 */
    public static ConfigPage listConfigs(TransportClient client, ListConfigsOptions options) {
        Query query = new Query();
        if (options != null) {
            query.addIfNotBlank("namespace", options.namespace);
            query.addIfNotBlank("groupName", options.groupName);
            query.addIfNotBlank("dataId", options.dataId);
            query.setIfPositive("pageNum", options.pageNum);
            query.setIfPositive("pageSize", options.pageSize);
        }
        JsonNode data = client.send("GET", API_CONFIGS, query, null);
        ConfigPage page = JsonUtil.convert(data, ConfigPage.class);
        return page != null ? page : new ConfigPage();
    }

    /**
     * 查询配置详情。读取成功时写入本地缓存；配置中心不可用（网络层异常）且本地存在该配置的
     * 缓存时，返回缓存内容。
     */
    public static ConfigItem getConfig(TransportClient client, String namespace, String group, String dataId) {
        Query query = new Query();
        query.addIfNotBlank("namespace", namespace);
        query.addIfNotBlank("groupName", group);
        query.set("dataId", dataId);
        try {
            JsonNode data = client.send("GET", API_CONFIG_DETAIL, query, null);
            ConfigItem item = JsonUtil.convert(data, ConfigItem.class);
            if (item == null) {
                throw new WaymarkException("waymark: 解析配置详情失败");
            }
            ConfigCache.write(client.cacheDir(), item);
            client.logger().debug("waymark: 拉取配置成功 namespace={} group={} dataId={}", namespace, group, dataId);
            return item;
        } catch (TransportException e) {
            if (!client.cacheDir().isEmpty()) {
                ConfigItem cached = ConfigCache.read(client.cacheDir(), namespace, group, dataId);
                if (cached != null) {
                    client.logger().warn("waymark: 配置中心不可用，回退本地缓存 namespace={} group={} dataId={}",
                            namespace, group, dataId);
                    return cached;
                }
            }
            throw e;
        }
    }

    /** 发布或更新配置。 */
    public static void publishConfig(TransportClient client, PublishConfigRequest request) {
        client.send("POST", API_CONFIGS, null, request);
        client.logger().info("waymark: 发布配置成功 namespace={} group={} dataId={}",
                request.namespace, request.groupName, request.dataId);
    }

    /** 删除配置。 */
    public static void deleteConfig(TransportClient client, String namespace, String group, String dataId) {
        Query query = new Query();
        query.addIfNotBlank("namespace", namespace);
        query.addIfNotBlank("groupName", group);
        query.set("dataId", dataId);
        client.send("DELETE", API_CONFIGS, query, null);
        client.logger().info("waymark: 删除配置成功 namespace={} group={} dataId={}", namespace, group, dataId);
    }

    /** 查询配置历史版本。服务端按页返回且单页有上限，这里循环拉取所有分页后合并为完整列表。 */
    public static List<ConfigHistory> configHistory(TransportClient client, String namespace, String group,
            String dataId) {
        List<ConfigHistory> result = new ArrayList<ConfigHistory>();
        for (int pageNum = 1; ; pageNum++) {
            Query query = new Query();
            query.addIfNotBlank("namespace", namespace);
            query.addIfNotBlank("groupName", group);
            query.set("dataId", dataId);
            query.set("pageNum", String.valueOf(pageNum));
            query.set("pageSize", String.valueOf(CONFIG_HISTORY_PAGE_SIZE));
            JsonNode data = client.send("GET", API_CONFIG_HISTORY, query, null);
            ConfigHistoryPage page = JsonUtil.convert(data, ConfigHistoryPage.class);
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

    /** 将指定历史版本还原为当前配置。 */
    public static void restoreConfig(TransportClient client, String namespace, String group, String dataId,
            long historyId) {
        RestoreRequest body = new RestoreRequest();
        body.namespace = namespace;
        body.groupName = group;
        body.dataId = dataId;
        body.historyId = historyId;
        client.send("POST", API_CONFIG_RESTORE, null, body);
    }

    /** 导出配置为 zip 字节流。 */
    public static byte[] exportConfigs(TransportClient client, ExportOptions options) {
        ExportRequest body = new ExportRequest();
        if (options != null) {
            body.namespace = options.namespace;
            body.groupName = options.groupName;
            body.dataId = options.dataId;
            body.items = options.items;
        }
        HttpResponse response = client.doRaw("POST", API_CONFIG_EXPORT, null, body, "application/json",
                client.defaultTimeoutMillis());
        // 导出失败时服务端返回 JSON 错误体，成功时返回 zip 二进制流。
        if (response.isJsonResponse()) {
            JsonNode node = JsonUtil.readTree(response.body);
            if (node != null && node.isObject()) {
                ResultVo result = JsonUtil.convert(node, ResultVo.class);
                if (result != null) {
                    ApiError error = result.toError(response.status);
                    if (error != null) {
                        throw error;
                    }
                }
            }
            throw new WaymarkException("waymark: 导出配置失败");
        }
        return response.body;
    }

    /**
     * 从 zip 字节流导入配置。
     * group 非空时全部导入到该分组，否则沿用压缩包中记录的原分组。
     */
    public static ImportResult importConfigs(TransportClient client, String namespace, String group, byte[] zipData) {
        String boundary = "----WaymarkBoundary" + System.nanoTime();
        byte[] payload = buildMultipart(boundary, namespace, group, zipData);
        JsonNode data = client.sendRawBody("POST", API_CONFIG_IMPORT, payload,
                "multipart/form-data; boundary=" + boundary, client.defaultTimeoutMillis());
        ImportResult result = JsonUtil.convert(data, ImportResult.class);
        return result != null ? result : new ImportResult();
    }

    private static byte[] buildMultipart(String boundary, String namespace, String group, byte[] zipData) {
        String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (namespace != null && !namespace.trim().isEmpty()) {
                writeField(out, boundary, "namespace", namespace.trim(), crlf);
            }
            if (group != null && !group.trim().isEmpty()) {
                writeField(out, boundary, "groupName", group.trim(), crlf);
            }
            out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"configs.zip\"" + crlf)
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(zipData == null ? new byte[0] : zipData);
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new WaymarkException("waymark: 构建导入请求失败", e);
        }
        return out.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value,
            String crlf) throws IOException {
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
    }

    /** 配置历史分页响应。 */
    static final class ConfigHistoryPage {

        public List<ConfigHistory> list;

        public long total;

        public int pageNum;

        public int pageSize;
    }

    /** 配置还原请求体。 */
    static final class RestoreRequest {

        String namespace;

        String groupName;

        String dataId;

        long historyId;
    }

    /** 配置导出请求体。 */
    static final class ExportRequest {

        String namespace;

        String groupName;

        String dataId;

        List<ExportItem> items;
    }
}
