package com.rumilance.kbprobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * サーバー別の計測統計を config/kbprobe.json へ永続化する。
 * Minecraft 同梱の Gson を使う（loader 以外の追加依存なし）。
 */
public final class StatsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, ServerStats>>() {
    }.getType();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("kbprobe.json");

    private static Map<String, ServerStats> stats;

    private StatsStore() {
    }

    public static synchronized ServerStats statsFor(String serverKey) {
        loadIfNeeded();
        return stats.computeIfAbsent(serverKey, k -> new ServerStats());
    }

    /** 計測済みサーバー (kbprobe.json のキー = 接続先ホスト名) の一覧。GUI の一覧表示用。 */
    public static synchronized java.util.Set<String> serverKeys() {
        loadIfNeeded();
        return java.util.Set.copyOf(stats.keySet());
    }

    public static synchronized void save() {
        if (stats == null) {
            return; // まだ誰も計測していない
        }
        try {
            Files.createDirectories(FILE.getParent());
            // 書き込み中のクラッシュで既存データを壊さないよう tmp 経由で置き換える
            Path tmp = FILE.resolveSibling("kbprobe.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                Map<String, Object> root = new HashMap<>();
                root.put("servers", stats);
                GSON.toJson(root, writer);
            }
            try {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // ストレージ失敗は計測を止めない
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadIfNeeded() {
        if (stats != null) {
            return;
        }
        stats = new HashMap<>();
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Map<String, Object> root = GSON.fromJson(reader, Map.class);
            if (root == null || !(root.get("servers") instanceof Map)) {
                return;
            }
            String serversJson = GSON.toJson(root.get("servers"));
            Map<String, ServerStats> loaded = GSON.fromJson(serversJson, MAP_TYPE);
            if (loaded != null) {
                stats.putAll(loaded);
            }
        } catch (IOException | RuntimeException ignored) {
            // 壊れた json は捨てて新規に計測を始める
        }
    }
}
