package com.wueasy.waymark;

/**
 * 服务端返回的业务错误，对应统一响应中的 code/msg 与 HTTP 状态码。
 */
public class ApiError extends WaymarkException {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final int httpStatus;

    public ApiError(int code, String msg, int httpStatus) {
        super(buildMessage(code, msg, httpStatus));
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /** 服务端业务返回码。 */
    public int getCode() {
        return code;
    }

    /** HTTP 状态码。 */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** 是否为未认证（令牌缺失或过期）。 */
    public boolean isUnauthorized() {
        return code == WaymarkConstants.CODE_UNAUTHORIZED || httpStatus == 401;
    }

    /** 是否为无权限。 */
    public boolean isForbidden() {
        return code == WaymarkConstants.CODE_FORBIDDEN || httpStatus == 403;
    }

    private static String buildMessage(int code, String msg, int httpStatus) {
        if (msg == null || msg.trim().isEmpty()) {
            return "waymark: 请求失败(code=" + code + ", status=" + httpStatus + ")";
        }
        return "waymark: " + msg + " (code=" + code + ")";
    }
}
