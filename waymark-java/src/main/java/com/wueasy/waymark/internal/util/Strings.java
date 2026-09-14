package com.wueasy.waymark.internal.util;

/**
 * 字符串处理工具，语义与 Go SDK 中的 strings 用法保持一致。
 */
public final class Strings {

    private Strings() {
    }

    /** 判断字符串是否为 null 或去除空白后为空。 */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 去除首尾空白，null 转换为空字符串。 */
    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** 去除首尾空白，空字符串转换为 null。 */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 去除首尾空白，为空时返回默认值。 */
    public static String normalizeKey(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : fallback;
    }
}
