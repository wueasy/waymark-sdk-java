package com.wueasy.waymark.internal.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.wueasy.waymark.TransportException;
import com.wueasy.waymark.WaymarkConstants;
import com.wueasy.waymark.internal.util.JsonUtil;
import com.wueasy.waymark.internal.util.Strings;
import com.wueasy.waymark.model.ConfigItem;

/**
 * 配置的本地缓存：配置中心不可用时由调用方回退读取。
 */
public final class ConfigCache {

    /** 系统用户缓存目录下的默认子目录名。 */
    public static final String DEFAULT_SUB_DIR = "waymark";

    private ConfigCache() {
    }

    /**
     * 解析本地缓存目录；禁用缓存时返回空字符串表示不启用。
     *
     * @param disable 是否禁用缓存
     * @param dir     自定义缓存目录，为空时使用系统用户缓存目录下的 waymark 目录
     */
    public static String resolveDir(boolean disable, String dir) {
        if (disable) {
            return "";
        }
        String trimmed = dir == null ? "" : dir.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        String base = userCacheDir();
        if (base.isEmpty()) {
            return "";
        }
        return new File(base, DEFAULT_SUB_DIR).getPath();
    }

    /** 判断异常是否为网络层不可达（连接失败、超时等），此类异常可回退到本地缓存。 */
    public static boolean isUnreachable(Throwable error) {
        return error instanceof TransportException;
    }

    /** 去除空白，为空时返回默认值。 */
    public static String normalizeKey(String value, String fallback) {
        return Strings.normalizeKey(value, fallback);
    }

    /**
     * 返回指定配置的缓存文件路径；缓存未启用时返回空字符串。
     * namespace、group 为空时按服务端默认值归一，保证写入与读取使用一致的缓存键。
     */
    public static String path(String dir, String namespace, String group, String dataId) {
        if (dir == null || dir.isEmpty()) {
            return "";
        }
        String ns = normalizeKey(namespace, WaymarkConstants.DEFAULT_NAMESPACE);
        String grp = normalizeKey(group, WaymarkConstants.DEFAULT_GROUP);
        String name = escapeSegment(ns) + "_" + escapeSegment(grp) + "_"
                + escapeSegment(Strings.trimToEmpty(dataId)) + ".json";
        return new File(new File(dir, "config"), name).getPath();
    }

    /** 将配置写入本地缓存。缓存为尽力而为，失败时静默忽略，不影响正常读取。 */
    public static void write(String dir, ConfigItem item) {
        if (item == null) {
            return;
        }
        String filePath = path(dir, item.namespace, item.groupName, item.dataId);
        if (filePath.isEmpty()) {
            return;
        }
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            return;
        }
        byte[] data;
        try {
            data = JsonUtil.toBytes(item);
        } catch (RuntimeException e) {
            return;
        }
        File tmp = null;
        try {
            // 先写临时文件再原子重命名，避免读取到写了一半的内容。
            tmp = File.createTempFile(".tmp-", "", parent);
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(data);
                out.flush();
            } finally {
                out.close();
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    /** 读取本地缓存的配置；缓存未启用、不存在或已损坏时返回 null。 */
    public static ConfigItem read(String dir, String namespace, String group, String dataId) {
        String filePath = path(dir, namespace, group, dataId);
        if (filePath.isEmpty()) {
            return null;
        }
        File file = new File(filePath);
        if (!file.isFile()) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            return JsonUtil.fromBytes(data, ConfigItem.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 将配置定位片段转义为文件系统安全且不产生歧义的名称。 */
    static String escapeSegment(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean safe = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '-';
            if (safe) {
                builder.append(ch);
            } else {
                builder.append('%').append(String.format("%02X", (int) ch));
            }
        }
        return builder.toString();
    }

    private static String userCacheDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.trim().isEmpty()) {
                return local.trim();
            }
            return joinHome("AppData/Local");
        }
        if (os.contains("mac")) {
            return joinHome("Library/Caches");
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        if (xdg != null && !xdg.trim().isEmpty()) {
            return xdg.trim();
        }
        return joinHome(".cache");
    }

    private static String joinHome(String sub) {
        String home = System.getProperty("user.home");
        if (home == null || home.trim().isEmpty()) {
            return "";
        }
        return new File(home.trim(), sub).getPath();
    }
}
