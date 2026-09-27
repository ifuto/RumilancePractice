package com.rumilance.practice.shieldweb;

import com.rumilance.practice.PluginIdentity;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.hiddenrank.HiddenRankService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.resourcepack.ResourcePackJson;
import com.rumilance.practice.resourcepack.ResourcePackService;
import com.rumilance.practice.shieldweb.ShieldWebServer.ShieldWebException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the Shield Web feature — the browser admin for the hidden
 * {@code custom_shield} rank, hosted by the server machine itself (optionally fronted by
 * Tailscale Funnel for remote admin / player pack downloads).
 *
 * <p>Flow when an admin uploads artwork and links it to a player:</p>
 * <ol>
 *   <li>PNG validated + normalised → injected into the pack working copy
 *       ({@code plugins/n-arena/shield-web/pack-src/}, unpacked once from the jar's
 *       {@code pack-base/} copy of the repo {@code resourcepack/});</li>
 *   <li>{@code shield.json} overrides regenerated from the registry; the zip is rebuilt
 *       deterministically → new SHA-1;</li>
 *   <li>the new hash is announced through {@link ResourcePackService} and the pack re-pushed
 *       to every online player (few seconds of download — the only way to land new textures);</li>
 *   <li>{@link HiddenRankService} grants {@code custom_shield} + the cmd, and the holder's
 *       live inventory shields receive the Custom Model Data instantly.</li>
 * </ol>
 *
 * <p>The server thread pool never touches Bukkit state directly: every mutation and every
 * player lookup is bounced onto the main thread via {@link #sync}. File IO and pack building
 * stay on whatever thread called in (they are slow-pathed on purpose).</p>
 */
public final class ShieldWebService implements ShieldWebServer.Api {

    private final Plugin plugin;
    private final ConfigService configService;
    private final HiddenRankService hiddenRanks;
    private final KitService kitService;
    private final ResourcePackService resourcePackService;
    private final Logger logger;

    private final Path rootDir;
    private final Path packSrc;
    private final Path packZip;
    private final ShieldRegistry registry;
    private final ShieldWebAuth auth;

    private ShieldWebServer server;
    private String lastBuiltSha1;
    private volatile boolean enabled;

    public ShieldWebService(Plugin plugin, ConfigService configService,
                            HiddenRankService hiddenRanks, KitService kitService,
                            ResourcePackService resourcePackService) {
        this.plugin = plugin;
        this.configService = configService;
        this.hiddenRanks = hiddenRanks;
        this.kitService = kitService;
        this.resourcePackService = resourcePackService;
        this.logger = plugin.getLogger();
        this.rootDir = PluginIdentity.dataFolder(plugin).toPath().resolve("shield-web");
        this.packSrc = rootDir.resolve("pack-src");
        this.packZip = rootDir.resolve("pack.zip");
        this.registry = new ShieldRegistry(rootDir.resolve("shields.json"));
        this.auth = new ShieldWebAuth(rootDir.resolve("token.txt"));
    }

    // ------------------------------------------------------------------ lifecycle

    /** Starts the embedded HTTP server when {@code shield-web.enabled} is on. Safe to call always. */
    public synchronized void start() {
        this.enabled = configService.config().getBoolean("shield-web.enabled", false);
        if (!enabled) {
            return;
        }
        String bind = configService.config().getString("shield-web.bind", "0.0.0.0");
        int port = configService.config().getInt("shield-web.port", 8765);
        boolean allowExternal = configService.config()
                .getBoolean("shield-web.admin-allow-external", false);
        try {
            unpackBaseIfNeeded();
            registry.load();
            if (!Files.isRegularFile(packZip) && Files.isRegularFile(packSrc.resolve("pack.mcmeta"))) {
                rebuildZip();
            }
            server = new ShieldWebServer(logger, auth, allowExternal, this);
            server.start(bind, port);
            if (managePackHash() && lastBuiltSha1 != null) {
                resourcePackService.updateLocalHash(lastBuiltSha1);
            }
            logger.info("[ShieldWeb] 盾管理Webを開始しました → http://" + bind + ":" + server.port()
                    + "/admin?token=" + auth.token());
            logger.info("[ShieldWeb] 外部公開は Tailscale Funnel 推奨: tailscale funnel --bg "
                    + port + " → resource-pack.json の url をその Funnel URL + /pack.zip に設定");
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "[ShieldWeb] 起動できませんでした（port " + port + " が使用中?）— 盾管理Webは無効のままです", e);
            closeServer();
        }
    }

    public synchronized void stop() {
        closeServer();
    }

    private void closeServer() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    public boolean enabled() {
        return enabled && server != null;
    }

    public int port() {
        return server == null ? -1 : server.port();
    }

    public String token() {
        return auth.token();
    }

    public int shieldCount() {
        return registry.list().size();
    }

    private boolean managePackHash() {
        return configService.config().getBoolean("shield-web.manage-pack-hash", true);
    }

    /** Extracts the bundled {@code pack-base/} (repo resourcepack/, shaded into the jar) once. */
    private void unpackBaseIfNeeded() throws IOException {
        if (Files.isRegularFile(packSrc.resolve("pack.mcmeta"))) {
            return; // operator's working copy (with their shields) already exists
        }
        java.io.File pluginJar = plugin.getFile();
        if (pluginJar == null || !pluginJar.isFile() || !pluginJar.getName().endsWith(".jar")) {
            throw new IOException("プラグインJARを特定できないため pack-base を展開できません");
        }
        try (JarFile jar = new JarFile(pluginJar)) {
            var entries = jar.entries();
            int extracted = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("pack-base/") || entry.isDirectory()) {
                    continue;
                }
                Path target = packSrc.resolve(name.substring("pack-base/".length()));
                Files.createDirectories(target.toAbsolutePath().getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                extracted++;
            }
            if (extracted == 0 || !Files.isRegularFile(packSrc.resolve("pack.mcmeta"))) {
                throw new IOException("jar 内に pack-base/ が見つかりません（開発環境からの起動?）");
            }
        }
        // A brand-new working copy keeps the repo's base shields: the registry must reflect
        // whatever _rumilance_shields entries ship inside it. Ours ships none (artwork is
        // operator-added at runtime), so the registry starts empty by definition.
    }

    /** Rebuilds {@code pack.zip} from the working copy and refreshes the announced hash. */
    private String rebuildZip() throws IOException {
        ShieldPackBuilder.buildZip(packSrc, packZip);
        lastBuiltSha1 = ShieldPackBuilder.sha1Hex(packZip);
        return lastBuiltSha1;
    }

    // ------------------------------------------------------------------ ShieldWebServer.Api

    @Override
    public byte[] packZip() {
        try {
            if (Files.isRegularFile(packZip)) {
                return Files.readAllBytes(packZip);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "[ShieldWeb] pack.zip の読み込みに失敗", e);
        }
        return null;
    }

    @Override
    public String adminHtml(String token) throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("shield-web/admin.html")) {
            if (in == null) {
                throw new IOException("admin.html が jar に同梱されていません");
            }
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return html.replace("__TOKEN__", token);
        }
    }

    @Override
    public byte[] shieldTexture(int cmd) {
        try {
            Path file = ShieldPackBuilder.textureFile(packSrc, cmd);
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    @Override
    public String stateJson() {
        long size = 0;
        try {
            size = Files.isRegularFile(packZip) ? Files.size(packZip) : 0;
        } catch (IOException ignored) {
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"ok\":true,\"port\":").append(port())
                .append(",\"manageHash\":").append(managePackHash())
                .append(",\"pack\":{\"present\":").append(size > 0)
                .append(",\"bytes\":").append(size)
                .append(",\"sha1\":").append(quote(lastBuiltSha1))
                .append(",\"url\":").append(quote(resourcePackService.packUrl()))
                .append("},\"shields\":[");
        boolean first = true;
        for (ShieldRegistry.ShieldEntry entry : registry.list()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"cmd\":").append(entry.cmd())
                    .append(",\"name\":").append(quote(entry.name()))
                    .append(",\"created\":").append(entry.createdEpochMillis())
                    .append('}');
        }
        sb.append("],\"holders\":[");
        first = true;
        for (UUID holder : hiddenRanks.customShieldHolders()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"uuid\":").append(quote(holder.toString()))
                    .append(",\"name\":").append(quote(hiddenRanks.lastName(holder)))
                    .append(",\"cmd\":").append(hiddenRanks.shieldModelData(holder))
                    .append('}');
        }
        // /urank web が発行したURLヒント用
        return sb.append("],\"tokenHint\":").append(quote(token())).append('}').toString();
    }

    @Override
    public String playersJson() {
        AtomicReference<String> out = new AtomicReference<>("{}");
        sync(() -> {
            StringBuilder sb = new StringBuilder("{\"online\":[");
            boolean first = true;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"name\":").append(quote(player.getName()))
                        .append(",\"uuid\":").append(quote(player.getUniqueId().toString()))
                        .append('}');
            }
            out.set(sb.append(']').toString());
        });
        return out.get();
    }

    @Override
    public String upload(int cmd, String name, byte[] png) throws ShieldWebException {
        requireEnabled();
        try {
            int finalCmd = cmd > 0 ? cmd : registry.nextCmd();
            // Validates + resizes BEFORE touching the registry, so a bad file changes nothing.
            ShieldPackBuilder.validatePng(png);
            ShieldRegistry.ShieldEntry entry = registry.upsert(finalCmd, name);
            registry.save();
            ShieldPackBuilder.inject(packSrc, finalCmd, png, registry.cmdList());
            String sha1 = rebuildZip();
            repushPack();
            int assignedCmd = finalCmd;
            String toast = "盾 cmd=" + assignedCmd + " を登録しました（新しいパックを全員に再送済み）";
            return "{\"ok\":true,\"cmd\":" + assignedCmd + ",\"name\":" + quote(entry.name())
                    + ",\"sha1\":" + quote(sha1) + ",\"message\":" + quote(toast) + "}";
        } catch (IOException e) {
            throw new ShieldWebException("画像の取り込みに失敗: " + e.getMessage(), e);
        }
    }

    @Override
    public String assign(int cmd, String playerRef) throws ShieldWebException {
        requireEnabled();
        if (registry.get(cmd).isEmpty()) {
            throw new ShieldWebException("cmd=" + cmd + " の盾は未登録です（先に画像をアップロード）");
        }
        Resolved resolved = resolvePlayer(playerRef);
        syncThrowing(() -> {
            hiddenRanks.setCustomShield(resolved.uuid(), resolved.name(), true);
            hiddenRanks.setShieldModelData(resolved.uuid(), cmd);
            Player online = Bukkit.getPlayer(resolved.uuid());
            if (online != null) {
                kitService.refreshCustomShield(online);
            }
        });
        String toast = resolved.name() + " に盾 cmd=" + cmd + " を適用しました"
                + (resolved.online() ? "（持っている盾へ即時反映）" : "（次回のキット適用から反映）");
        return "{\"ok\":true,\"player\":" + quote(resolved.name()) + ",\"cmd\":" + cmd
                + ",\"message\":" + quote(toast) + "}";
    }

    @Override
    public String unassign(String playerRef) throws ShieldWebException {
        requireEnabled();
        Resolved resolved = resolvePlayer(playerRef);
        syncThrowing(() -> {
            hiddenRanks.setCustomShield(resolved.uuid(), resolved.name(), false);
            Player online = Bukkit.getPlayer(resolved.uuid());
            if (online != null) {
                kitService.clearCustomShield(online);
            }
        });
        String toast = resolved.name() + " のカスタム盾を解除しました";
        return "{\"ok\":true,\"player\":" + quote(resolved.name())
                + ",\"message\":" + quote(toast) + "}";
    }

    @Override
    public String deleteShield(int cmd) throws ShieldWebException {
        requireEnabled();
        if (registry.get(cmd).isEmpty()) {
            throw new ShieldWebException("cmd=" + cmd + " の盾は未登録です");
        }
        List<String> unassigned = new ArrayList<>();
        syncThrowing(() -> {
            for (UUID holder : hiddenRanks.customShieldHolders()) {
                if (hiddenRanks.shieldModelData(holder) == cmd) {
                    hiddenRanks.setShieldModelData(holder, 0);
                    unassigned.add(hiddenRanks.lastName(holder));
                    Player online = Bukkit.getPlayer(holder);
                    if (online != null) {
                        kitService.clearCustomShield(online);
                    }
                }
            }
        });
        try {
            registry.remove(cmd);
            registry.save();
            ShieldPackBuilder.remove(packSrc, cmd, registry.cmdList());
            String sha1 = rebuildZip();
            repushPack();
            String toast = "盾 cmd=" + cmd + " を削除しました"
                    + (unassigned.isEmpty() ? "" : "（" + String.join(", ", unassigned)
                    + " の紐づけも解除）");
            return "{\"ok\":true,\"cmd\":" + cmd + ",\"sha1\":" + quote(sha1)
                    + ",\"unassigned\":" + unassigned.size()
                    + ",\"message\":" + quote(toast) + "}";
        } catch (IOException e) {
            throw new ShieldWebException("盾の削除に失敗: " + e.getMessage(), e);
        }
    }

    @Override
    public String repush() throws ShieldWebException {
        requireEnabled();
        try {
            String sha1 = rebuildZip();
            repushPack();
            return "{\"ok\":true,\"sha1\":" + quote(sha1)
                    + ",\"message\":\"最新のパックを全員に再送しました\"}";
        } catch (IOException e) {
            throw new ShieldWebException("パック再構築に失敗: " + e.getMessage(), e);
        }
    }

    /** Announces the freshly built hash and re-pushes the pack to everyone, on the main thread. */
    private void repushPack() {
        final String sha1 = lastBuiltSha1;
        if (managePackHash() && sha1 != null) {
            sync(() -> resourcePackService.updateLocalHash(sha1));
        } else {
            sync(resourcePackService::reload);
        }
    }

    private void requireEnabled() throws ShieldWebException {
        if (!enabled()) {
            throw new ShieldWebException("shield-web が停止中です（config.yml → shield-web.enabled）");
        }
    }

    // ------------------------------------------------------------------ player resolution (main thread)

    private record Resolved(UUID uuid, String name, boolean online) {
    }

    private Resolved resolvePlayer(String ref) throws ShieldWebException {
        AtomicReference<Resolved> out = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        sync(() -> {
            String trimmed = ref.trim();
            try {
                UUID uuid = UUID.fromString(trimmed);
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                Player online = Bukkit.getPlayer(uuid);
                String name = online != null ? online.getName() : offline.getName();
                if (online != null || name != null || hiddenRanks.lastName(uuid) != null) {
                    out.set(new Resolved(uuid, name != null ? name
                            : hiddenRanks.lastName(uuid), online != null));
                    return;
                }
                error.set("そのUUIDのプレイヤーは一度も参加していません: " + trimmed);
                return;
            } catch (IllegalArgumentException ignored) {
                // not a uuid — treat as name below
            }
            Player online = Bukkit.getPlayerExact(trimmed);
            if (online != null) {
                out.set(new Resolved(online.getUniqueId(), online.getName(), true));
                return;
            }
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(trimmed);
            if (cached != null && (cached.getName() != null || cached.hasPlayedBefore())) {
                out.set(new Resolved(cached.getUniqueId(),
                        cached.getName() != null ? cached.getName() : trimmed, false));
                return;
            }
            error.set("プレイヤーが見つかりません（オンライン名 or 参加済みの名前/UUID）: " + trimmed);
        });
        if (out.get() == null) {
            throw new ShieldWebException(error.get() != null ? error.get()
                    : "プレイヤーを解決できませんでした");
        }
        return out.get();
    }

    /** Runs {@code task} on the server's main thread and waits (bounded) for completion. */
    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                failure.set(e);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                logger.warning("[ShieldWeb] main-thread task timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private void syncThrowing(Runnable task) throws ShieldWebException {
        try {
            sync(task);
        } catch (RuntimeException e) {
            throw new ShieldWebException("適用に失敗: " + e.getMessage(), e);
        }
    }

    private static String quote(String text) {
        return ResourcePackJson.quote(text == null ? "" : text);
    }
}
