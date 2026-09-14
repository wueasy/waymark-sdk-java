package com.wueasy.waymark.internal.subscribe;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wueasy.waymark.ApiError;
import com.wueasy.waymark.NoCredentialsException;
import com.wueasy.waymark.WaymarkConstants;
import com.wueasy.waymark.WaymarkException;
import com.wueasy.waymark.internal.transport.Query;
import com.wueasy.waymark.internal.transport.ResultVo;
import com.wueasy.waymark.internal.transport.StreamConnection;
import com.wueasy.waymark.internal.transport.TransportClient;
import com.wueasy.waymark.internal.util.JsonUtil;
import com.wueasy.waymark.internal.util.Threads;
import com.wueasy.waymark.model.Event;
import com.wueasy.waymark.model.SubscribeOptions;

/**
 * 一条配置/实例变更的 SSE 订阅。内部使用守护线程接收事件，连接断开后自动重连；
 * 收到变更事件时同步回调 {@link SubscribeOptions#handler}。使用 {@link #close()} 释放。
 */
public final class Subscription implements Closeable {

    private static final String SUBSCRIBE_PATH = "/api/subscribe";

    private final TransportClient client;

    private final Query query;

    private final Consumer<Event> handler;

    private final long reconnectDelayMillis;

    private final Thread worker;

    private volatile boolean running = true;

    private volatile StreamConnection current;

    private Subscription(TransportClient client, Query query, Consumer<Event> handler, long reconnectDelayMillis) {
        this.client = client;
        this.query = query;
        this.handler = handler;
        this.reconnectDelayMillis = reconnectDelayMillis > 0
                ? reconnectDelayMillis
                : WaymarkConstants.DEFAULT_RECONNECT_DELAY_MILLIS;
        this.worker = Threads.daemonFactory("waymark-subscribe").newThread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        });
    }

    /**
     * 建立订阅并启动后台接收线程，立即返回。
     *
     * @param client  transport 客户端
     * @param options 订阅参数，dataId 传 "*" 表示订阅该分组下全部配置变更、为空表示不订阅配置，
     *                serviceName 为空表示订阅该分组下全部服务变更
     */
    public static Subscription start(TransportClient client, SubscribeOptions options) {
        Query query = new Query();
        query.addIfNotBlank("namespace", options.namespace);
        query.addIfNotBlank("groupName", options.groupName);
        if (options.dataIds != null) {
            for (String dataId : options.dataIds) {
                query.addIfNotBlank("dataId", dataId);
            }
        }
        query.addIfNotBlank("serviceName", options.serviceName);
        Subscription subscription = new Subscription(client, query, options.handler,
                options.reconnectDelayMillis);
        subscription.worker.start();
        return subscription;
    }

    /** 订阅是否仍在运行。 */
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                streamOnce();
            } catch (NoCredentialsException e) {
                client.logger().error("waymark: 订阅终止（未配置账号密码）: {}", e.getMessage());
                return;
            } catch (ApiError e) {
                if (e.isForbidden()) {
                    client.logger().error("waymark: 订阅终止（无权限）: {}", e.getMessage());
                    return;
                }
                if (!running) {
                    return;
                }
                client.logger().warn("waymark: 订阅断开，将在 {}ms 后重连: {}", reconnectDelayMillis, e.getMessage());
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                client.logger().warn("waymark: 订阅断开，将在 {}ms 后重连: {}", reconnectDelayMillis, e.getMessage());
            }
            if (!running) {
                return;
            }
            try {
                Thread.sleep(reconnectDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 建立一次 SSE 连接并持续解析事件，直到连接结束或订阅被关闭。 */
    private void streamOnce() {
        client.ensureToken();
        StreamConnection stream = client.stream(SUBSCRIBE_PATH, query);
        this.current = stream;
        try {
            if (stream.status != 200) {
                throw toError(stream);
            }
            client.logger().info("waymark: 订阅已建立");
            parseEvents(stream.input());
        } finally {
            this.current = null;
            stream.close();
        }
    }

    /** 将非 200 响应解析为异常；令牌失效且可重登时清除令牌，重连时将重新登录。 */
    private RuntimeException toError(StreamConnection stream) {
        byte[] body = readAll(stream.input());
        JsonNode node = JsonUtil.readTree(body);
        if (node != null && node.isObject()) {
            ResultVo result = JsonUtil.convert(node, ResultVo.class);
            if (result != null) {
                ApiError error = result.toError(stream.status);
                if (error != null) {
                    if (error.isUnauthorized() && client.canRelogin()) {
                        client.setToken("");
                    }
                    return error;
                }
            }
        }
        return new WaymarkException("waymark: 订阅失败(status=" + stream.status + ")");
    }

    /** 解析 SSE 数据流并派发变更事件。 */
    private void parseEvents(InputStream input) {
        if (input == null) {
            return;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String eventName = "";
        StringBuilder data = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        client.logger().debug("waymark: 收到订阅事件 event={}", eventName);
                        dispatch(eventName, data.toString());
                    }
                    eventName = "";
                    data.setLength(0);
                } else if (line.startsWith(":")) {
                    // 注释行（心跳），忽略。
                } else if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).trim());
                }
            }
        } catch (IOException e) {
            throw new WaymarkException("waymark: 读取订阅数据失败: " + e.getMessage(), e);
        }
    }

    /** 解析并派发单个 SSE 事件，仅处理变更事件（事件名为空或 change）。 */
    private void dispatch(String eventName, String payload) {
        if (handler == null) {
            return;
        }
        if (!eventName.isEmpty() && !"change".equals(eventName)) {
            return;
        }
        Event event;
        try {
            event = JsonUtil.fromJson(payload, Event.class);
        } catch (RuntimeException e) {
            return;
        }
        if (event != null) {
            handler.accept(event);
        }
    }

    private static byte[] readAll(InputStream input) {
        if (input == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** 关闭订阅并断开底层连接，唤醒阻塞读取的接收线程。 */
    @Override
    public void close() {
        if (!running) {
            return;
        }
        running = false;
        StreamConnection stream = current;
        if (stream != null) {
            stream.close();
        }
        worker.interrupt();
    }

    /** 等待接收线程退出，最多等待指定毫秒。 */
    public void awaitTermination(long timeoutMillis) {
        try {
            worker.join(timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
