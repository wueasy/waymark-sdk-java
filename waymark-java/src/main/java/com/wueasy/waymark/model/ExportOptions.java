package com.wueasy.waymark.model;

import java.util.List;

/**
 * 导出配置参数。
 * {@code items} 非空时按指定配置导出；否则按 namespace/groupName/dataId 过滤导出全部匹配配置。
 */
public class ExportOptions {

    public String namespace;

    public String groupName;

    public String dataId;

    public List<ExportItem> items;
}
