package com.wueasy.waymark.internal.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.wueasy.waymark.ApiError;

/**
 * 服务端统一响应结构：{@code {code, successful, msg, data, encrypt}}。
 */
public class ResultVo {

    public int code;

    public boolean successful;

    public String msg;

    public JsonNode data;

    public boolean encrypt;

    /** 将失败响应转换为 {@link ApiError}，成功时返回 null。 */
    public ApiError toError(int httpStatus) {
        if (successful) {
            return null;
        }
        return new ApiError(code, msg, httpStatus);
    }
}
