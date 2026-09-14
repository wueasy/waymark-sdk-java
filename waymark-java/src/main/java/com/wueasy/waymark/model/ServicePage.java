package com.wueasy.waymark.model;

import java.util.List;

/**
 * 分页服务概览。
 */
public class ServicePage {

    public List<ServiceSummary> list;

    public long total;

    public int pageNum;

    public int pageSize;
}
