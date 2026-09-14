package com.wueasy.waymark.internal.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wueasy.waymark.WaymarkException;

/**
 * JSON 序列化/反序列化工具，内部共享一个线程安全的 ObjectMapper。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private JsonUtil() {
    }

    /** 返回共享的 ObjectMapper。 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** 将对象序列化为 JSON 字节数组。 */
    public static byte[] toBytes(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (IOException e) {
            throw new WaymarkException("waymark: 序列化请求体失败", e);
        }
    }

    /** 将对象序列化为 JSON 字符串。 */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new WaymarkException("waymark: 序列化请求体失败", e);
        }
    }

    /** 解析 JSON 字节数组为树模型，解析失败时返回 null。 */
    public static JsonNode readTree(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return MAPPER.readTree(data);
        } catch (IOException e) {
            return null;
        }
    }

    /** 解析 JSON 字符串为树模型，解析失败时返回 null。 */
    public static JsonNode readTree(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(data);
        } catch (IOException e) {
            return null;
        }
    }

    /** 解析 JSON 字符串为指定类型。 */
    public static <T> T fromJson(String data, Class<T> type) {
        try {
            return MAPPER.readValue(data, type);
        } catch (IOException e) {
            throw new WaymarkException("waymark: 解析响应数据失败", e);
        }
    }

    /** 解析 JSON 字节数组为指定类型。 */
    public static <T> T fromBytes(byte[] data, Class<T> type) {
        try {
            return MAPPER.readValue(data, type);
        } catch (IOException e) {
            throw new WaymarkException("waymark: 解析响应数据失败", e);
        }
    }

    /** 将树模型转换为指定类型，node 为空时返回 null。 */
    public static <T> T convert(JsonNode node, Class<T> type) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return MAPPER.treeToValue(node, type);
        } catch (IOException e) {
            throw new WaymarkException("waymark: 解析响应数据失败", e);
        }
    }

    /** 将树模型转换为列表，node 为空时返回空列表。 */
    public static <T> List<T> convertList(JsonNode node, Class<T> elementType) {
        List<T> result = new ArrayList<T>();
        if (node == null || node.isNull() || !node.isArray()) {
            return result;
        }
        for (JsonNode element : node) {
            T value = convert(element, elementType);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /** 将字节数组按 UTF-8 转为字符串（用于错误信息展示）。 */
    public static String asString(byte[] data) {
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }
}
