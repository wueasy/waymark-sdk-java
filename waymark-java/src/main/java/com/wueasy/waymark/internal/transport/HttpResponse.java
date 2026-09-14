package com.wueasy.waymark.internal.transport;

/**
 * 原始 HTTP 响应（状态码、内容类型与响应体）。
 */
public final class HttpResponse {

    public final int status;

    public final String contentType;

    public final byte[] body;

    public HttpResponse(int status, String contentType, byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    /** 响应内容类型是否为 JSON。 */
    public boolean isJsonResponse() {
        return contentType != null && contentType.contains("application/json");
    }
}
