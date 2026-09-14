package com.wueasy.waymark.model;

/**
 * 变更事件（订阅推送），仅包含定位信息，收到后需自行拉取最新数据。
 */
public class Event {

    /** 事件类型：CONFIG（配置变更）/ INSTANCE（实例变更）。 */
    public String eventType;

    public String namespace;

    public String group;

    /** 配置变更时为 dataId，实例变更时为 serviceName。 */
    public String watchKey;

    public String md5;

    @Override
    public String toString() {
        return "Event{eventType=" + eventType + ", namespace=" + namespace + ", group=" + group
                + ", watchKey=" + watchKey + ", md5=" + md5 + '}';
    }
}
