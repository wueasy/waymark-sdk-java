package com.wueasy.waymark.model;

/**
 * 配置项。
 */
public class ConfigItem {

    public long id;

    public String namespace;

    public String groupName;

    public String dataId;

    public String content;

    public String md5;

    /** 配置类型：text/json/yaml/properties/xml。 */
    public String type;

    public long createTime;

    public long updateTime;

    /** 返回配置内容，内容为空时返回空字符串。 */
    public String contentOrEmpty() {
        return content == null ? "" : content;
    }
}
