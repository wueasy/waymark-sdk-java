package com.wueasy.waymark.model;

import java.util.List;

/**
 * 配置导入结果统计。
 */
public class ImportResult {

    public int imported;

    /** 导入失败的配置标识列表。 */
    public List<String> failed;
}
