package com.wueasy.waymark.internal.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次 SSE 长连接。读取超时被设置为不限时，关闭时同时断开底层连接，
 * 以便阻塞在读取上的线程及时退出。
 */
public final class StreamConnection implements Closeable {

    private final HttpURLConnection connection;

    private final InputStream input;

    private final Runnable onClose;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** HTTP 状态码。 */
    public final int status;

    /** 响应内容类型。 */
    public final String contentType;

    StreamConnection(HttpURLConnection connection, int status, String contentType, InputStream input, Runnable onClose) {
        this.connection = connection;
        this.status = status;
        this.contentType = contentType;
        this.input = input;
        this.onClose = onClose;
    }

    /** 响应体输入流，状态码非 200 时可能为 null。 */
    public InputStream input() {
        return input;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
                // 关闭失败无需处理
            }
        }
        connection.disconnect();
        if (onClose != null) {
            onClose.run();
        }
    }
}
