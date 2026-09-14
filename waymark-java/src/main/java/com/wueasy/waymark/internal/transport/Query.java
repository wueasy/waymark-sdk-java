package com.wueasy.waymark.internal.transport;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wueasy.waymark.WaymarkException;

/**
 * 查询参数构建器，支持同一参数名重复出现（如订阅多个 dataId）。
 */
public final class Query {

    private final Map<String, List<String>> params = new LinkedHashMap<String, List<String>>();

    /** 追加一个参数值（允许重复），值为 null 时忽略。 */
    public Query add(String key, String value) {
        if (value == null) {
            return this;
        }
        List<String> values = params.get(key);
        if (values == null) {
            values = new ArrayList<String>();
            params.put(key, values);
        }
        values.add(value);
        return this;
    }

    /** 追加一个参数值，空白值忽略并去除首尾空白。 */
    public Query addIfNotBlank(String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            add(key, value.trim());
        }
        return this;
    }

    /** 覆盖设置参数值，值为 null 时忽略。 */
    public Query set(String key, String value) {
        if (value == null) {
            return this;
        }
        params.remove(key);
        return add(key, value);
    }

    /** 覆盖设置参数值，仅在数值大于 0 时写入。 */
    public Query setIfPositive(String key, long value) {
        if (value > 0) {
            set(key, String.valueOf(value));
        }
        return this;
    }

    /** 是否没有任何参数。 */
    public boolean isEmpty() {
        return params.isEmpty();
    }

    /** 编码为查询字符串（不含前导问号）。 */
    public String encode() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            for (String value : entry.getValue()) {
                if (builder.length() > 0) {
                    builder.append('&');
                }
                builder.append(urlEncode(entry.getKey())).append('=').append(urlEncode(value));
            }
        }
        return builder.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new WaymarkException("waymark: 编码查询参数失败", e);
        }
    }
}
