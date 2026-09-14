package com.wueasy.waymark.model;

/**
 * 发布配置参数。
 */
public class PublishConfigRequest {

    public String namespace;

    public String groupName;

    public String dataId;

    public String content;

    /** 配置类型：text/json/yaml/properties/xml，为空时服务端按 text 处理。 */
    public String type;
}
