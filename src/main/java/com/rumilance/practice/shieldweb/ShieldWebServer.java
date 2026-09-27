package com.rumilance.practice.shieldweb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin HTTP layer for Shield Web, on the JDK's built-in {@link HttpServer} — zero added
 * dependencies, zero extra processes: it lives and dies with the plugin.
 *
 * <p>Routing table (all mutations are POST so no browser preflight/CORS dance is needed):</p>
 *
 * <pre>
 *   GET  /pack.zip                     open — the pack players download (Tailscale Funnel fronted)
 *   GET  /shield-texture?cmd=N         admin — texture preview for the UI
 *   GET  /admin                        admin — single-page management UI (token auto-injected)
 *   GET  /admin/api/state              admin — full state JSON
 *   GET  /admin/api/players            admin — online players
 *   POST /admin/api/upload?cmd&name    admin — raw PNG body → create/replace artwork
 *   POST /admin/api/assign?cmd&player  admin — link artwork to a player (+ live equip)
 *   POST /admin/api/unassign?player    admin — remove the link (keeps nothing)
 *   POST /admin/api/delete?cmd         admin — delete artwork from pack (+ unassign holders)
 *   POST /admin/api/repush             admin — rebuild zip + re-push to everyone
 * </pre>
 *
 * <p>Admin routes require the bearer token AND a private-network source address (see
 * {@link ShieldWebAuth}). The pack route is open by design.</p>
 */
public final class ShieldWebServer implements AutoCloseable {

    /** Service-side handlers; the server stays free of Minecraft types. */
    public interface Api {
        byte[] packZip();

        String adminHtml(String token) throws IOException;

        byte[] shieldTexture(int cmd);

        String stateJson();

        String playersJson();

        String upload(int cmd, String name, byte[] png) throws ShieldWebException;

        String assign(int cmd, String playerRef) throws ShieldWebException;

        String unassign(String playerRef) throws ShieldWebException;

        String deleteShield(int cmd) throws ShieldWebException;

        String repush() throws ShieldWebException;

        // ---- server management (the "upgrade to admin site" set) ----

        /** Pack integrity probes + per-player applied state ("動作テスト"). */
        String selftestJson();

        /** Executes a console command on the main thread; rate-limited service-side. */
        String runCommand(String command) throws ShieldWebException;

        /** Native tab-completion candidates for a partially typed command line. */
        String completionsJson(String input);

        /** The tail of logs/latest.log, hard-bounded on both axes. */
        String logsJson(int lines);

        /** Finished matches across every player (battle log), newest first. */
        String battlesJson();

        /** Currently running matches with live per-player state (lightweight spectate). */
        String matchesJson();
    }

    /** Uniform error for anything the admin UI should surface as a message. */
    public static final class ShieldWebException extends Exception {
        public ShieldWebException(String message) {
            super(message);
        }

        public ShieldWebException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Logger logger;
    private final ShieldWebAuth auth;
    private final boolean allowExternalAdmin;
    private final Api api;
    private HttpServer server;
    private ExecutorService executor;

    public ShieldWebServer(Logger logger, ShieldWebAuth auth, boolean allowExternalAdmin, Api api) {
        this.logger = logger;
        this.auth = auth;
        this.allowExternalAdmin = allowExternalAdmin;
        this.api = api;
    }

    public void start(String bind, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 16);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "N Arena ShieldWeb-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newFixedThreadPool(2, factory);
        server.setExecutor(executor);
        server.createContext("/", this::route);
        server.start();
    }

    public int port() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    private void route(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path == null) {
                respond(exchange, 404, "text/plain; charset=utf-8", "not found");
                return;
            }
            if (path.equals("/pack.zip")) {
                handlePack(exchange);
                return;
            }
            if (path.equals("/") || path.equals("/admin") || path.equals("/admin/")
                    || path.startsWith("/admin/api/") || path.equals("/shield-texture")) {
                handleAdmin(exchange, path);
                return;
            }
            respond(exchange, 404, "text/plain; charset=utf-8", "not found");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ShieldWeb] request failed: " + exchange.getRequestURI(), t);
            try {
                respond(exchange, 500, "application/json; charset=utf-8",
                        "{\"error\":\"internal error\"}");
            } catch (IOException ignored) {
                // connection already gone
            }
        } finally {
            exchange.close();
        }
    }

    private void handlePack(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")
                && !exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
            respond(exchange, 405, "text/plain; charset=utf-8", "method not allowed");
            return;
        }
        byte[] zip = api.packZip();
        if (zip == null) {
            respond(exchange, 404, "text/plain; charset=utf-8", "pack not built yet");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"RumilanceResourcePack.zip\"");
        exchange.sendResponseHeaders(200, zip.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(zip);
        }
    }

    private void handleAdmin(HttpExchange exchange, String path) throws IOException {
        if (!ShieldWebAuth.isAllowedSource(exchange.getRemoteAddress().getAddress(),
                allowExternalAdmin)) {
            respond(exchange, 403, "application/json; charset=utf-8",
                    "{\"error\":\"管理画面はLAN/Tailscale内からのみ開けます\"}");
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String presented = exchange.getRequestHeaders().getFirst("Authorization");
        if ((presented == null || presented.isBlank()) && query.containsKey("token")) {
            presented = query.get("token");
        }
        if (!auth.tokenMatches(presented)) {
            respond(exchange, 401, "application/json; charset=utf-8",
                    "{\"error\":\"トークンが違います。/urank web で正しいURLを確認してください\"}");
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase();
        try {
            switch (path) {
                case "/", "/admin", "/admin/" -> {
                    if (!method.equals("GET")) {
                        respond(exchange, 405, json(), methodNotAllowed());
                        return;
                    }
                    respond(exchange, 200, "text/html; charset=utf-8",
                            api.adminHtml(auth.token()), false);
                    return;
                }
                case "/shield-texture" -> {
                    byte[] png = api.shieldTexture(intParam(query, "cmd"));
                    if (png == null) {
                        respond(exchange, 404, json(), error("このCMDの盾はありません"));
                    } else {
                        respond(exchange, 200, "image/png", png, false);
                    }
                    return;
                }
                case "/admin/api/state" -> jsonOk(exchange, api.stateJson());
                case "/admin/api/players" -> jsonOk(exchange, api.playersJson());
                case "/admin/api/upload" -> {
                    requirePost(method);
                    byte[] body = readBody(exchange, ShieldPackBuilder.MAX_UPLOAD_BYTES + 1);
                    jsonOk(exchange, api.upload(intParamOrDefault(query, "cmd", 0),
                            query.getOrDefault("name", ""), body));
                }
                case "/admin/api/assign" -> {
                    requirePost(method);
                    jsonOk(exchange, api.assign(intParam(query, "cmd"),
                            required(query, "player")));
                }
                case "/admin/api/unassign" -> {
                    requirePost(method);
                    jsonOk(exchange, api.unassign(required(query, "player")));
                }
                case "/admin/api/delete" -> {
                    requirePost(method);
                    jsonOk(exchange, api.deleteShield(intParam(query, "cmd")));
                }
                case "/admin/api/repush" -> {
                    requirePost(method);
                    jsonOk(exchange, api.repush());
                }
                case "/admin/api/selftest" -> jsonOk(exchange, api.selftestJson());
                case "/admin/api/complete" ->
                    jsonOk(exchange, api.completionsJson(query.getOrDefault("input", "")));
                case "/admin/api/logs" ->
                    jsonOk(exchange, api.logsJson(intParamOrDefault(query, "lines", 200)));
                case "/admin/api/battles" -> jsonOk(exchange, api.battlesJson());
                case "/admin/api/matches" -> jsonOk(exchange, api.matchesJson());
                case "/admin/api/command" -> {
                    requirePost(method);
                    byte[] body = readBody(exchange, 4096);
                    String command = new String(body, StandardCharsets.UTF_8).trim();
                    if (command.isEmpty()) {
                        command = required(query, "cmd");
                    }
                    jsonOk(exchange, api.runCommand(command));
                }
                default -> respond(exchange, 404, json(), error("unknown endpoint"));
            }
        } catch (ShieldWebException e) {
            String message = e.getMessage() == null ? "error" : e.getMessage();
            if (e.getCause() != null) {
                logger.log(Level.WARNING, "[ShieldWeb] " + message, e.getCause());
            }
            respond(exchange, 400, json(), error(message));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "[ShieldWeb] handler bug on " + path, e);
            respond(exchange, 400, json(), error("内部エラー: " + e.getMessage()));
        }
    }

    private void requirePost(String method) throws ShieldWebException {
        if (!method.equals("POST")) {
            throw new ShieldWebException("POST で呼んでください");
        }
    }

    private void jsonOk(HttpExchange exchange, String json) throws IOException {
        respond(exchange, 200, json(), json == null ? "{}" : json);
    }

    private static String json() {
        return "application/json; charset=utf-8";
    }

    private static String methodNotAllowed() {
        return error("method not allowed");
    }

    static String error(String message) {
        StringBuilder sb = new StringBuilder("{\"error\":");
        sb.append('"');
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return sb.append("\"}").toString();
    }

    private void respond(HttpExchange exchange, int code, String contentType, String body)
            throws IOException {
        respond(exchange, code, contentType, body.getBytes(StandardCharsets.UTF_8), true);
    }

    private void respond(HttpExchange exchange, int code, String contentType, String body,
                         boolean noStore) throws IOException {
        respond(exchange, code, contentType, body.getBytes(StandardCharsets.UTF_8), noStore);
    }

    private void respond(HttpExchange exchange, int code, String contentType, byte[] body,
                         boolean noStore) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (noStore) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
        }
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] readBody(HttpExchange exchange, int maxBytes) throws IOException {
        try (var in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(maxBytes);
            if (body.length > ShieldPackBuilder.MAX_UPLOAD_BYTES) {
                throw new IOException("アップロードが "
                        + (ShieldPackBuilder.MAX_UPLOAD_BYTES / 1024 / 1024) + "MB を超えています");
            }
            return body;
        }
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            } else {
                map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static int intParam(Map<String, String> query, String key) throws ShieldWebException {
        String raw = query.get(key);
        if (raw == null || raw.isBlank()) {
            throw new ShieldWebException(key + " パラメータが必要です");
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) {
                throw new NumberFormatException("negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ShieldWebException(key + " は正の整数で指定してください: " + raw);
        }
    }

    private static int intParamOrDefault(Map<String, String> query, String key, int fallback) {
        String raw = query.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String required(Map<String, String> query, String key) throws ShieldWebException {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw new ShieldWebException(key + " パラメータが必要です");
        }
        return value.trim();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
