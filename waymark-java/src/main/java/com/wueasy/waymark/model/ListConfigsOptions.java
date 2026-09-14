package com.wueasy.waymark.model;

/**
 * 配置列表查询条件。
 */
public class ListConfigsOptions {

    /** 命名空间，为空时使用默认命名空间。 */
    public String namespace;

    /** 分组，为空表示不过滤。 */
    public String groupName;

    /** 配置标识，为空表示不过滤。 */
    public String dataId;

    /** 页码，从 1 开始。 */
    public int pageNum;

    /** 每页条数。 */
    public int pageSize;
}
