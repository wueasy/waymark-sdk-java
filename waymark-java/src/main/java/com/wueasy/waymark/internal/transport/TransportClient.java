package com.wueasy.waymark.internal.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.wueasy.waymark.ApiError;
import com.wueasy.waymark.ClientConfig;
import com.wueasy.waymark.NoCredentialsException;
import com.wueasy.waymark.TransportException;
import com.wueasy.waymark.WaymarkException;
import com.wueasy.waymark.internal.subscribe.SubscriptionHub;
import com.wueasy.waymark.internal.util.JsonUtil;
import com.wueasy.waymark.internal.util.Strings;
import com.wueasy.waymark.model.LoginResult;
import com.wueasy.waymark.model.UserProfile;

/**
 * 底层 HTTP 传输与会话管理：统一响应解析、Bearer 认证、令牌过期自动重登重试、
 * SSE 长连接与原始响应读取。供 internal 下各功能子包复用。
 */
public final class TransportClient {

    private static final String APPLICATION_JSON = "application/json";

    private static final String AUTHORIZATION = "Authorization";

    /** 服务端地址列表，至少一个，按配置顺序排列。 */
    private final List<String> endpoints;

    private final String username;

    private final String password;

    private final int defaultTimeoutMillis;

    /** 本地配置缓存目录，为空表示未启用缓存。 */
    private final String cacheDir;

    private final Logger logger;

    private final Object tokenLock = new Object();

    private volatile String token;

    /** 当前使用的服务端地址下标，网络层错误时递增以故障转移。 */
    private volatile int active;

    private final Set<StreamConnection> openStreams =
            Collections.newSetFromMap(new ConcurrentHashMap<StreamConnection, Boolean>());

    /** 共享订阅管理：按命名空间与分组复用同一条 SSE 连接。 */
    private final SubscriptionHub subscriptionHub = new SubscriptionHub(this);

    public TransportClient(ClientConfig config) {
        this.endpoints = parseEndpoints(config.getEndpoint());
        this.username = Strings.trimToEmpty(config.getUsername());
        this.password = config.getPassword() == null ? "" : config.getPassword();
        this.defaultTimeoutMillis = config.getTimeoutMillis();
        this.cacheDir = com.wueasy.waymark.internal.cache.ConfigCache
                .resolveDir(config.isDisableCache(), config.getCacheDir());
        Logger configured = config.getLogger();
        this.logger = configured != null ? configured : LoggerFactory.getLogger("com.wueasy.waymark");
        this.token = Strings.trimToEmpty(config.getToken());
    }

    /**
     * 解析逗号分隔的服务端地址：去除空白与尾部斜杠、去重并校验协议与主机。
     * 空输入或全部为空时抛出 WaymarkException。
     */
    private static List<String> parseEndpoints(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new WaymarkException("waymark: endpoint 不能为空");
        }
        List<String> endpoints = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String part : raw.split(",")) {
            String endpoint = part.trim();
            if (endpoint.isEmpty()) {
                continue;
            }
            if (!isValidEndpoint(endpoint)) {
                throw new WaymarkException(
                        "waymark: endpoint 需包含协议与主机，例如 http://127.0.0.1:9868（错误值：" + endpoint + "）");
            }
            endpoint = trimTrailingSlash(endpoint);
            if (seen.add(endpoint)) {
                endpoints.add(endpoint);
            }
        }
        if (endpoints.isEmpty()) {
            throw new WaymarkException("waymark: endpoint 不能为空");
        }
        return endpoints;
    }

    private static boolean isValidEndpoint(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            return uri.getScheme() != null && !uri.getScheme().isEmpty() && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    /** 当前使用的服务端地址（已去除尾部斜杠）。 */
    public String endpoint() {
        return endpoints.get(active);
    }

    /** 全部已配置的服务端地址（副本）。 */
    public List<String> endpoints() {
        return new ArrayList<String>(endpoints);
    }

    /** 切换到下一个服务端地址；仅配置了多个地址时生效。 */
    public void failover() {
        if (endpoints.size() > 1) {
            synchronized (tokenLock) {
                active = (active + 1) % endpoints.size();
            }
        }
    }

    /** 已配置的服务端地址数量。 */
    private int endpointCount() {
        return endpoints.size();
    }

    /** 本地配置缓存目录，为空表示未启用缓存。 */
    public String cacheDir() {
        return cacheDir;
    }

    /** 日志记录器。 */
    public Logger logger() {
        return logger;
    }

    /** 共享订阅管理：配置监听与实例订阅复用同一条 SSE 连接。 */
    public SubscriptionHub sharedSubscriptions() {
        return subscriptionHub;
    }

    /** 单次请求默认超时时间（毫秒）。 */
    public int defaultTimeoutMillis() {
        return defaultTimeoutMillis;
    }

    /** 设置访问令牌。 */
    public void setToken(String token) {
        synchronized (tokenLock) {
            this.token = Strings.trimToEmpty(token);
        }
    }

    /** 返回当前访问令牌。 */
    public String token() {
        return token;
    }

    /** 是否具备自动登录所需的账号信息。 */
    public boolean canRelogin() {
        return !username.isEmpty() && !password.isEmpty();
    }

    /** 确保存在访问令牌，缺失时尝试自动登录。 */
    public void ensureToken() {
        if (!token().isEmpty()) {
            return;
        }
        login();
    }

    /** 使用客户端配置的账号密码登录并缓存令牌。 */
    public LoginResult login() {
        if (!canRelogin()) {
            throw new NoCredentialsException();
        }
        return loginWith(username, password);
    }

    /** 使用指定账号密码登录并缓存令牌。 */
    public LoginResult loginWith(String loginUsername, String loginPassword) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("username", loginUsername);
        body.put("password", loginPassword);
        JsonNode data = sendNoAuth("POST", "/api/auth/login", body, defaultTimeoutMillis);
        LoginResult result = JsonUtil.convert(data, LoginResult.class);
        if (result == null) {
            throw new WaymarkException("waymark: 解析登录结果失败");
        }
        setToken(result.token);
        logger.debug("waymark: 登录成功，用户 {}", loginUsername);
        return result;
    }

    /** 查询系统是否已初始化。 */
    public boolean initStatus() {
        JsonNode data = sendNoAuth("GET", "/api/auth/init-status", null, defaultTimeoutMillis);
        JsonNode initialized = data == null ? null : data.get("initialized");
        return initialized != null && initialized.asBoolean(false);
    }

    /** 初始化首个管理员账号。 */
    public void init(String initUsername, String initPassword, String nickname) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("username", initUsername);
        body.put("password", initPassword);
        body.put("nickname", nickname);
        sendNoAuth("POST", "/api/auth/init", body, defaultTimeoutMillis);
    }

    /** 查询当前登录用户资料。 */
    public UserProfile profile() {
        JsonNode data = send("GET", "/api/auth/profile", null, null, defaultTimeoutMillis);
        return JsonUtil.convert(data, UserProfile.class);
    }

    /** 发送请求并返回统一响应中的 data 节点（默认超时）。 */
    public JsonNode send(String method, String path, Query query, Object body) {
        return send(method, path, query, body, defaultTimeoutMillis);
    }

    /**
     * 发送请求并返回统一响应中的 data 节点。令牌过期时自动重新登录并重试一次。
     *
     * @param timeoutMillis 单次请求超时时间（毫秒）
     */
    public JsonNode send(String method, String path, Query query, Object body, int timeoutMillis) {
        ensureToken();
        byte[] payload = body == null ? null : JsonUtil.toBytes(body);
        HttpResponse response = execute(method, path, query, payload, body == null ? null : APPLICATION_JSON,
                true, timeoutMillis);
        ApiError error = toApiError(response);
        if (error == null) {
            return parseResult(response).data;
        }
        if (!error.isUnauthorized() || !canRelogin()) {
            throw error;
        }
        logger.debug("waymark: 令牌失效，尝试重新登录后重试 {} {}", method, path);
        try {
            login();
        } catch (RuntimeException loginError) {
            logger.error("waymark: 重新登录失败", loginError);
            throw error;
        }
        HttpResponse retry = execute(method, path, query, payload, body == null ? null : APPLICATION_JSON,
                true, timeoutMillis);
        ApiError retryError = toApiError(retry);
        if (retryError != null) {
            throw retryError;
        }
        return parseResult(retry).data;
    }

    /** 发送自定义请求体（如 multipart 导入），返回统一响应中的 data 节点。 */
    public JsonNode sendRawBody(String method, String path, byte[] payload, String contentType, int timeoutMillis) {
        ensureToken();
        HttpResponse response = execute(method, path, null, payload, contentType, true, timeoutMillis);
        ApiError error = toApiError(response);
        if (error == null) {
            return parseResult(response).data;
        }
        if (!error.isUnauthorized() || !canRelogin()) {
            throw error;
        }
        login();
        HttpResponse retry = execute(method, path, null, payload, contentType, true, timeoutMillis);
        ApiError retryError = toApiError(retry);
        if (retryError != null) {
            throw retryError;
        }
        return parseResult(retry).data;
    }

    /** 发送请求并返回原始响应，用于处理非统一响应的接口（如 zip 导出）。 */
    public HttpResponse doRaw(String method, String path, Query query, Object body, String contentType,
            int timeoutMillis) {
        ensureToken();
        byte[] payload = body == null ? null : JsonUtil.toBytes(body);
        return execute(method, path, query, payload, contentType, true, timeoutMillis);
    }

    /** 建立 SSE 长连接（不做超时限制，由调用方关闭）。多地址时网络层失败会自动切换地址重试。 */
    public StreamConnection stream(String path, Query query) {
        ensureToken();
        int total = endpointCount();
        TransportException lastError = null;
        for (int attempt = 0; attempt < total; attempt++) {
            try {
                return streamOnce(path, query);
            } catch (TransportException e) {
                lastError = e;
                if (total > 1 && attempt < total - 1) {
                    failover();
                    logger.warn("waymark: 订阅连接失败，切换到地址 {}: {}", endpoint(), e.getMessage());
                }
            }
        }
        throw lastError;
    }

    private StreamConnection streamOnce(String path, Query query) {
        URL url = buildUrl(path, query);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(defaultTimeoutMillis);
            // 长连接不设置读取超时，由关闭连接来终止
            connection.setReadTimeout(0);
            connection.setRequestProperty("Accept", "text/event-stream");
            String current = token();
            if (!current.isEmpty()) {
                connection.setRequestProperty(AUTHORIZATION, "Bearer " + current);
            }
            int status = connection.getResponseCode();
            String contentType = connection.getHeaderField("Content-Type");
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            // 使用数组持有引用，保证关闭回调移除的是连接本身而非 Runnable。
            final StreamConnection[] holder = new StreamConnection[1];
            final StreamConnection stream = new StreamConnection(connection, status, contentType, input,
                    new Runnable() {
                        @Override
                        public void run() {
                            openStreams.remove(holder[0]);
                        }
                    });
            holder[0] = stream;
            openStreams.add(stream);
            return stream;
        } catch (IOException e) {
            if (connection != null) {
                connection.disconnect();
            }
            throw new TransportException("waymark: 订阅失败: " + e.getMessage(), e);
        }
    }

    /** 关闭客户端并断开所有进行中的长连接。 */
    public void close() {
        subscriptionHub.close();
        for (StreamConnection stream : openStreams) {
            stream.close();
        }
        openStreams.clear();
    }

    private JsonNode sendNoAuth(String method, String path, Object body, int timeoutMillis) {
        byte[] payload = body == null ? null : JsonUtil.toBytes(body);
        HttpResponse response = execute(method, path, null, payload, body == null ? null : APPLICATION_JSON,
                false, timeoutMillis);
        ApiError error = toApiError(response);
        if (error != null) {
            throw error;
        }
        return parseResult(response).data;
    }

    private ApiError toApiError(HttpResponse response) {
        return parseResult(response).toError(response.status);
    }

    private ResultVo parseResult(HttpResponse response) {
        byte[] body = response.body;
        if (body == null || body.length == 0) {
            ResultVo empty = new ResultVo();
            empty.successful = response.status < 400;
            empty.code = response.status;
            return empty;
        }
        JsonNode node = JsonUtil.readTree(body);
        if (node == null || !node.isObject()) {
            // 非统一响应体：以 HTTP 状态判定成败
            if (response.status >= 400) {
                throw new ApiError(response.status, truncate(JsonUtil.asString(body)), response.status);
            }
            throw new WaymarkException("waymark: 解析响应失败(status=" + response.status + ")");
        }
        ResultVo result = JsonUtil.convert(node, ResultVo.class);
        if (result == null) {
            result = new ResultVo();
            result.successful = response.status < 400;
            result.code = response.status;
        }
        return result;
    }

    /**
     * 发送一次请求；配置了多个地址时，遇到网络层错误会依次切换到下一个地址重试（故障转移），
     * 业务错误不做转移。
     */
    private HttpResponse execute(String method, String path, Query query, byte[] payload, String contentType,
            boolean withAuth, int timeoutMillis) {
        int total = endpointCount();
        TransportException lastError = null;
        for (int attempt = 0; attempt < total; attempt++) {
            try {
                return executeOnce(method, path, query, payload, contentType, withAuth, timeoutMillis);
            } catch (TransportException e) {
                lastError = e;
                if (total > 1 && attempt < total - 1) {
                    failover();
                    logger.warn("waymark: 请求失败，切换到地址 {}: {}", endpoint(), e.getMessage());
                }
            }
        }
        throw lastError;
    }

    private HttpResponse executeOnce(String method, String path, Query query, byte[] payload, String contentType,
            boolean withAuth, int timeoutMillis) {
        URL url = buildUrl(path, query);
        logger.debug("waymark: 请求 {} {}", method, url);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestProperty("Accept", APPLICATION_JSON);
            if (withAuth) {
                String current = token();
                if (!current.isEmpty()) {
                    connection.setRequestProperty(AUTHORIZATION, "Bearer " + current);
                }
            }
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        contentType != null ? contentType : APPLICATION_JSON);
                connection.setFixedLengthStreamingMode(payload.length);
                OutputStream output = connection.getOutputStream();
                output.write(payload);
                output.flush();
                output.close();
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] body = readAll(input);
            String responseType = connection.getHeaderField("Content-Type");
            return new HttpResponse(status, responseType, body);
        } catch (IOException e) {
            logger.error("waymark: 请求失败 {} {}: {}", method, url, e.getMessage());
            throw new TransportException("waymark: 请求失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private URL buildUrl(String path, Query query) {
        String target = endpoint() + path;
        if (query != null && !query.isEmpty()) {
            target = target + "?" + query.encode();
        }
        try {
            return new URL(target);
        } catch (MalformedURLException e) {
            throw new WaymarkException("waymark: 构建请求地址失败: " + target, e);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }
}
