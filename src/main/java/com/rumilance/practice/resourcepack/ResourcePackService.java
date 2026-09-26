package com.rumilance.practice.resourcepack;

import com.rumilance.practice.config.ConfigService;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Distributes the server resource pack to players directly from the plugin — no
 * {@code resource-pack=} / {@code require-resource-pack=} entries in server.properties are
 * needed.
 *
 * <p><b>Where the pack comes from ({@code resource-pack.json} in the plugin data folder).</b>
 * The operator edits this file by hand:</p>
 *
 * <pre>
 * {
 *   "url": "https://github.com/&lt;owner&gt;/&lt;repo&gt;/releases/download/vX.Y.Z/RumilanceResourcePack.zip",
 *   "prompt": "...",                 (optional)
 *   "required": false,               (optional)
 *   "min-client-protocol": 0,        (optional)
 *   "sha1": "..."                    (written by the server — do not edit)
 * }
 * </pre>
 *
 * <ul>
 *   <li>the {@code url} in the file wins; when the key is missing the shipped Release URL is
 *       used, so a fresh install works out of the box;</li>
 *   <li><b>the SHA-1 is fetched from that URL on every server start</b> (the zip itself is
 *       downloaded and hashed), then written back into the file — the hash can therefore never
 *       drift from the published pack, which is what used to make every client reject it;</li>
 *   <li>if the URL cannot be reached, the last known hash in the file is used (offline / LAN
 *       setups keep working);</li>
 *   <li>Publishing the pack is two scripts (there is no release workflow — the app token this
 *       repository is pushed with cannot write {@code .github/workflows/*}):
 *       {@code tools/release/build-pack.sh} rebuilds {@code dist/RumilanceResourcePack.zip} from
 *       {@code resourcepack/} — validating that every font provider's texture exists and that no
 *       texture is left unwired — and writes the {@code .sha1} in {@code sha1sum -c} format;
 *       {@code tools/release/attach-pack.sh <tag>} then uploads both to that Release.
 *       Re-uploading to the tag this URL already points at needs no config change at all,
 *       because the hash is re-fetched from the URL at boot.</li>
 * </ul>
 *
 * <p><b>Policy</b> (choosable in the admin GUI, persisted to {@code pack-policy.yml}):
 * <ul>
 *   <li><b>required</b> — players who decline the pack or whose download fails are kicked
 *       (the classic behaviour),</li>
 *   <li><b>recommended</b> (default) — players may decline and keep playing; the rank badges
 *       are image-only, so such a client simply sees no badge.</li>
 * </ul>
 * The service tracks who actually applied the pack ({@link #hasPack(Player)}).</p>
 *
 * <p>Note: when this service is enabled, remove any {@code resource-pack*} lines from
 * server.properties — otherwise clients may be asked to apply the pack twice.</p>
 */
public final class ResourcePackService implements Listener {

    /** Shipped default: the immutable GitHub Release asset (used when the JSON has no url). */
    public static final String DEFAULT_URL =
            "https://github.com/ifuto/RumilancePractice/releases/download/v1.76.52/RumilanceResourcePack.zip";
    /** Operator-owned pack definition (url + prompt + the hash the server maintains). */
    private static final String JSON_FILE_NAME = "resource-pack.json";

    /** Small delay after join so login-time packets settle before the pack prompt. */
    private static final long APPLY_DELAY_TICKS = 10L;
    /** Admin-GUI policy override file (survives the config.yml resource-pack force-sync). */
    private static final String POLICY_FILE_NAME = "pack-policy.yml";
    /**
     * Failed downloads are retried this many times before a player is kicked. Pack
     * downloads occasionally fail once because of transient network hiccups (a new
     * match can start while the client is still downloading), and an instant kick for
     * a single FAILED_DOWNLOAD felt like a random disconnect from the player's side.
     */
    private static final int MAX_DOWNLOAD_RETRIES = 2;
    /** Delay before a failed download is retried. */
    private static final long RETRY_DELAY_TICKS = 40L;

    private final Plugin plugin;
    private final ConfigService configService;
    private final Logger logger;

    /** Cached request; rebuilt by {@link #reload()} (null = disabled or misconfigured). */
    private volatile ResourcePackRequest request;
    /** Id of our pack — used to recognise our own status events (and keep us on top). */
    private volatile UUID packId;
    /** Per-player count of failed download attempts since the last successful apply. */
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    /** Per-player pack state: TRUE = our glyphs render on that client. Absent = unknown yet. */
    private final Map<UUID, Boolean> packApplied = new ConcurrentHashMap<>();
    /** Request id most recently sent to each player; status events from another pack are ignored. */
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();
    /** Admin-GUI policy override ({@code pack-policy.yml}); null = use the JSON/config default. */
    private volatile Boolean requiredOverride;
    /** Values read from {@code resource-pack.json} (operator-owned). */
    private volatile String jsonUrl;
    private volatile String jsonPrompt;
    private volatile String jsonSha1;
    private volatile Boolean jsonRequired;
    private volatile int jsonMinProtocol = -1;
    /** SHA-1 hashed from the actual zip at the configured URL on this start (null = unknown). */
    private volatile String liveSha1;

    public ResourcePackService(Plugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.logger = plugin.getLogger();
        loadPolicy();
        loadJson();
        reload();
    }

    /**
     * Re-reads {@code resource-pack.*} from config.yml and pushes the fresh pack to all
     * online players (clients that already applied it skip the download thanks to the
     * unchanged URL + SHA-1 pair). Safe to call from {@code /rumireload}.
     */
    public void reload() {
        loadJson();
        this.request = buildRequest();
        ResourcePackRequest built = this.request;
        if (built == null) {
            this.packId = null;
            this.packApplied.clear();
            this.pendingRequests.clear();
            this.failedAttempts.clear();
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            applyTo(online);
        }
        resolveHashFromUrl();
    }

    /** Whether plugin-side distribution is enabled at all. */
    public boolean enabled() {
        return configService.config().getBoolean("resource-pack.enabled", true);
    }

    /** URL the clients are told to download from (JSON value, else the shipped Release URL). */
    public String packUrl() {
        return configuredUrl();
    }

    /** SHA-1 announced to clients: resolved from the URL this start, else the stored value. */
    public String packSha1() {
        return liveSha1 != null ? liveSha1 : jsonSha1;
    }

    /**
     * Whether players refusing/failing the pack must be kicked. The admin-GUI override
     * ({@code pack-policy.yml}) wins over {@code resource-pack.required} in config.yml, which
     * is force-synced with the bundled pack on every startup. Default: recommended (no kick).
     */
    public boolean required() {
        Boolean override = this.requiredOverride;
        if (override != null) {
            return override;
        }
        Boolean fromJson = this.jsonRequired;
        if (fromJson != null) {
            return fromJson;
        }
        return configService.config().getBoolean("resource-pack.required", false);
    }

    /**
     * Switches the pack policy from the admin GUI and persists it. The pack is re-pushed to
     * everyone with the new {@code required} flag (clients re-prompt on the next apply).
     */
    public void setRequired(boolean required) {
        this.requiredOverride = required;
        savePolicy(required);
        logger.info(() -> "Resource pack policy set to "
                + (required ? "REQUIRED (kick on decline)" : "RECOMMENDED (join without)"));
        reload();
    }

    /**
     * Whether the player's client actually applied this service's current resource pack (our
     * glyphs render). Unknown state (fresh join or download in flight) is deliberately false;
     * viewers use text badges until this request reports SUCCESSFULLY_LOADED.
     */
    public boolean hasPack(Player player) {
        if (player == null) {
            return false;
        }
        // Only SUCCESSFULLY_LOADED for this service's current request proves that our
        // glyph font is present. Player#hasResourcePack can refer to another plugin's pack
        // (or an old server.properties pack), which caused inconsistent per-viewer badges.
        return Boolean.TRUE.equals(packApplied.get(player.getUniqueId()));
    }

    private File policyFile() {
        return new File(com.rumilance.practice.PluginIdentity.dataFolder(plugin), POLICY_FILE_NAME);
    }

    private void loadPolicy() {
        try {
            File file = policyFile();
            if (!file.isFile()) {
                return;
            }
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            if (yaml.isBoolean("required")) {
                this.requiredOverride = yaml.getBoolean("required");
            }
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not read " + POLICY_FILE_NAME, e);
        }
    }

    private void savePolicy(boolean required) {
        try {
            // Hand-written so admins see what the file means; Bukkit's YamlConfiguration
            // does not emit comments on save.
            File file = policyFile();
            File parent = file.getParentFile();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent.toPath());
            }
            java.nio.file.Files.writeString(file.toPath(),
                    "# Resource-pack policy chosen in the admin GUI (NARENA).\n"
                            + "# required: true  = kick players who decline the pack or fail to download it.\n"
                            + "# required: false = players may decline and keep playing (rank badges fall\n"
                            + "#                   back to the text prefixes N / N+ / OWNER on their client).\n"
                            + "required: " + required + "\n",
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            logger.log(Level.WARNING, "Could not save " + POLICY_FILE_NAME, e);
        }
    }

    /** Players already noted (in the log) as running a client too old for this pack. */
    private final java.util.Set<UUID> tooOldNotified = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Cached {@code Player#getProtocolVersion} lookup; resolved lazily, may stay null. */
    private static volatile java.lang.reflect.Method protocolMethod;
    private static volatile boolean protocolMethodResolved;

    /**
     * The client's protocol version, or {@link PackFormatPolicy#UNKNOWN} when this server API
     * does not expose it. Reflected because {@code getProtocolVersion()} is not part of the
     * compile-time API surface here; failing to read it must never lock a player out of the
     * pack, so an unreadable value falls back to "no data".
     */
    private static int clientProtocolOf(Player player) {
        try {
            java.lang.reflect.Method method = protocolMethod;
            if (method == null) {
                if (protocolMethodResolved) {
                    return PackFormatPolicy.UNKNOWN;
                }
                protocolMethodResolved = true;
                method = player.getClass().getMethod("getProtocolVersion");
                protocolMethod = method;
            }
            Object value = method.invoke(player);
            return value instanceof Integer protocol ? protocol : PackFormatPolicy.UNKNOWN;
        } catch (Throwable t) {
            return PackFormatPolicy.UNKNOWN;
        }
    }

    /** Sends the pack to the player (no-op when disabled or misconfigured). */
    public void applyTo(Player player) {
        ResourcePackRequest toSend = this.request;
        UUID currentPackId = this.packId;
        if (toSend == null || currentPackId == null || player == null || !player.isOnline()) {
            return;
        }
        // ViaVersion translates the protocol but never the resource pack: an old client
        // receives this pack byte for byte and answers "broken or incompatible", and the
        // custom-font glyphs would stay missing even if it were forced. Skip the prompt
        // instead — the UI already falls back for players without the pack.
        int minProtocol = configuredMinProtocol();
        if (minProtocol > 0) {
            int clientProtocol = clientProtocolOf(player);
            if (PackFormatPolicy.tooOld(clientProtocol, minProtocol)) {
                if (!tooOldNotified.add(player.getUniqueId())) {
                    logger.info(() -> "Resource pack not offered to " + player.getName()
                            + ": client protocol " + clientProtocol + " is older than "
                            + minProtocol + " (resource-pack.min-client-protocol).");
                }
                packApplied.put(player.getUniqueId(), Boolean.FALSE);
                return;
            }
        }
        UUID playerId = player.getUniqueId();
        pendingRequests.put(playerId, currentPackId);
        // A new request is not applied until its own success event arrives. This prevents a
        // stale failure/success from a previous URL or another plugin from changing the
        // current viewer's fallback state.
        packApplied.remove(playerId);
        try {
            player.sendResourcePacks(toSend);
        } catch (Throwable t) {
            // A broken send (odd client, plugin acting up mid-shutdown, ...) must never
            // bubble up into the join/status handler that called us. Mark it as failed and
            // let the normal retry path try again for transient server-side errors.
            packApplied.put(playerId, Boolean.FALSE);
            logger.log(Level.WARNING, "Failed to send the resource pack to " + player.getName(), t);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && pendingRequests.get(playerId) == currentPackId) {
                    applyTo(player);
                }
            }, RETRY_DELAY_TICKS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        // A reconnect is a new client session. Do not reuse a SUCCESS state from the old
        // connection, otherwise the first TAB refresh can show glyphs before this client has
        // downloaded the pack.
        failedAttempts.remove(playerId);
        packApplied.remove(playerId);
        pendingRequests.remove(playerId);
        if (this.request == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applyTo(player);
            }
        }, APPLY_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        failedAttempts.remove(playerId);
        packApplied.remove(playerId);
        pendingRequests.remove(playerId);
    }

    /**
     * Tracks the pack state of every player and, when the pack policy is REQUIRED, kicks
     * players who refuse or fail it. DECLINED = the player pressed "No" in the pack dialog;
     * FAILED_DOWNLOAD / INVALID_URL / FAILED_RELOAD = the client never got the pack, which is
     * retried a couple of times first. When the policy is RECOMMENDED (default) nobody is
     * kicked — the client is simply marked pack-less so the nametag / TAB prefixes fall back
     * to the text badges (N / N+ / OWNER). Players whose client already applied the pack are
     * NEVER kicked nor demoted: a late failure status for a re-send must not affect someone
     * who is playing with the pack active.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        UUID expectedId = pendingRequests.get(playerId);
        UUID statusId = event.getID();
        UUID ourId = this.packId;
        // Paper normally supplies the request UUID. A few older client/protocol paths emit
        // null, so accept null only while this player has an outstanding request. A concrete
        // different UUID is definitely another pack and must not alter our state.
        boolean belongsToOurRequest = expectedId != null
                && (statusId == null || expectedId.equals(statusId));
        if (!belongsToOurRequest) {
            if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED
                    && this.request != null && ourId != null) {
                // Another pack finished: re-pin ours, but never mark that other pack as ours.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        applyTo(player);
                    }
                });
            }
            return;
        }
        if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            if (ourId != null && expectedId.equals(ourId)) {
                failedAttempts.remove(playerId);
                packApplied.put(playerId, Boolean.TRUE);
            } else if (this.request != null && ourId != null) {
                // Keep OUR pack pinned to the top of the client's Selected list: whenever some
                // other pack (another plugin, a /pack command...) finishes applying, re-send
                // ours so it is the most recent pack again and therefore stays on top. Our own
                // SUCCESS events are recognised by the stable pack id and never re-trigger
                // this (no loop).
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        applyTo(player);
                    }
                });
            }
            return;
        }
        if (this.request == null) {
            return;
        }
        boolean downloadFailure =
                status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                        || status == PlayerResourcePackStatusEvent.Status.INVALID_URL
                        || status == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD;
        if (status != PlayerResourcePackStatusEvent.Status.DECLINED && !downloadFailure) {
            return;
        }
        // The pack is already applied — any late failure status is stale/duplicate noise.
        // Do not use Player#hasResourcePack here: it may describe another plugin's pack.
        if (Boolean.TRUE.equals(packApplied.get(playerId))) {
            return;
        }
        if (downloadFailure) {
            int attempts = failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
            if (attempts <= MAX_DOWNLOAD_RETRIES) {
                logger.info("Resource pack " + status.name().toLowerCase(java.util.Locale.ROOT)
                        + " for " + player.getName() + " — retrying ("
                        + attempts + "/" + MAX_DOWNLOAD_RETRIES + ")");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        applyTo(player);
                    }
                }, RETRY_DELAY_TICKS);
                return;
            }
        }
        failedAttempts.remove(player.getUniqueId());
        // The client refused or gave up: no glyphs on this client — prefixes fall back to
        // the text badges. The scoreboard tick picks the change up on its next refresh.
        packApplied.put(player.getUniqueId(), Boolean.FALSE);
        if (!required()) {
            logger.info(() -> player.getName() + " keeps playing without the resource pack ("
                    + status.name().toLowerCase(java.util.Locale.ROOT)
                    + ") — text rank badges apply");
            return;
        }
        String message = configService.config().getString("resource-pack.kick-message",
                "This server requires the N Arena resource pack.");
        logger.info(() -> "Kicking " + player.getName()
                + " — resource pack " + status.name().toLowerCase(java.util.Locale.ROOT));
        player.kick(Component.text(message));
    }

    // ------------------------------------------------------------------ resource-pack.json

    /** The operator-owned pack file. */
    private File jsonFile() {
        return new File(com.rumilance.practice.PluginIdentity.dataFolder(plugin), JSON_FILE_NAME);
    }

    /**
     * The URL the server serves, in order of authority: {@code resource-pack.json} (the file the
     * operator edits by hand) → {@code resource-pack.url} in config.yml (legacy/offline setups)
     * → the shipped Release URL, so a fresh install always works.
     */
    String configuredUrl() {
        if (jsonUrl != null && !jsonUrl.isBlank()) {
            return jsonUrl.trim();
        }
        String legacy = configService.config().getString("resource-pack.url", "");
        return legacy == null || legacy.isBlank() ? DEFAULT_URL : legacy.trim();
    }

    /** Prompt text: the JSON value, else config.yml, else the built-in default. */
    private String configuredPrompt() {
        if (jsonPrompt != null && !jsonPrompt.isBlank()) {
            return jsonPrompt;
        }
        return configService.config().getString("resource-pack.prompt",
                "Required for N Arena icons.");
    }

    /** Protocol gate: the JSON value, else config.yml. */
    private int configuredMinProtocol() {
        if (jsonMinProtocol >= 0) {
            return jsonMinProtocol;
        }
        return configService.config().getInt("resource-pack.min-client-protocol", 0);
    }

    /**
     * Reads {@code resource-pack.json}, creating it with the shipped Release URL on first start
     * so the operator only has to replace the URL (or paste a new release's URL) by hand.
     */
    private void loadJson() {
        File file = jsonFile();
        try {
            if (!file.isFile()) {
                Map<String, String> defaults = new java.util.LinkedHashMap<>();
                defaults.put("url", ResourcePackJson.quote(DEFAULT_URL));
                defaults.put("prompt", ResourcePackJson.quote(
                        configService.config().getString("resource-pack.prompt",
                                "Required for N Arena icons.")));
                defaults.put("required", "false");
                defaults.put("min-client-protocol", "0");
                defaults.put("sha1", ResourcePackJson.quote(""));
                File parent = file.getParentFile();
                if (parent != null) {
                    java.nio.file.Files.createDirectories(parent.toPath());
                }
                java.nio.file.Files.writeString(file.toPath(), ResourcePackJson.write(defaults),
                        StandardCharsets.UTF_8);
                logger.info("Created " + JSON_FILE_NAME + " — edit its \"url\" to point at your"
                        + " Release asset (the SHA-1 is filled in automatically on startup).");
            }
            Map<String, String> values = ResourcePackJson.parse(
                    java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8));
            jsonUrl = values.get("url");
            jsonPrompt = values.get("prompt");
            jsonSha1 = normalizeSha1(values.get("sha1"));
            String required = values.get("required");
            jsonRequired = parseBoolean(required);
            String minProtocol = values.get("min-client-protocol");
            if (minProtocol != null && minProtocol.matches("\\d+")) {
                jsonMinProtocol = Integer.parseInt(minProtocol);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not read " + JSON_FILE_NAME
                    + " — falling back to the shipped Release pack URL.", e);
        }
    }

    /** Writes the resolved URL/hash back so the file always shows what clients are told. */
    private void saveJson(String url, String sha1) {
        File file = jsonFile();
        try {
            Map<String, String> values = new java.util.LinkedHashMap<>();
            values.put("url", ResourcePackJson.quote(url == null || url.isBlank()
                    ? configuredUrl() : url));
            // (keys below mirror what this service resolved, so the file always documents the
            // pack clients are actually told about)
            values.put("prompt", ResourcePackJson.quote(configuredPrompt()));
            values.put("required", String.valueOf(required()));
            values.put("min-client-protocol", String.valueOf(configuredMinProtocol()));
            values.put("sha1", ResourcePackJson.quote(sha1));
            java.nio.file.Files.writeString(file.toPath(), ResourcePackJson.write(values),
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            logger.log(Level.WARNING, "Could not update " + JSON_FILE_NAME, e);
        }
    }

    private static Boolean parseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.equals("true")) {
            return Boolean.TRUE;
        }
        if (value.equals("false")) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** 40 hex chars or null (an empty/"auto" placeholder means "not resolved yet"). */
    private static String normalizeSha1(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return value.matches("[0-9a-f]{40}") ? value : null;
    }

    // ------------------------------------------------------------------------ pack request

    /**
     * Builds the pack request from {@code resource-pack.json} (URL, prompt, protocol gate) and
     * the best known SHA-1, or {@code null} while impossible (disabled / no URL / no hash yet).
     */
    private ResourcePackRequest buildRequest() {
        if (!enabled()) {
            return null;
        }
        String url = configuredUrl();
        // Authority: the hash resolved from the URL on this start → the value stored in the
        // JSON by an earlier start → the config.yml fallback (offline / LAN setups).
        String sha1Hex = liveSha1 != null ? liveSha1 : jsonSha1;
        if (sha1Hex == null) {
            sha1Hex = normalizeSha1(configService.config()
                    .getString("resource-pack.sha1", ""));
        }
        if (sha1Hex == null) {
            return null; // still resolving: the fetch completion pushes the request
        }
        return buildRequest(url, sha1Hex);
    }

    /**
     * Builds the pack request with explicit url/hash. Returns {@code null} when misconfigured
     * (a warning is logged so admins can spot the problem).
     */
    private ResourcePackRequest buildRequest(String url, String sha1Hex) {
        String prompt = configuredPrompt();
        if (url == null || url.isBlank()) {
            logger.warning("resource-pack.json has no url — plugin pack distribution disabled.");
            return null;
        }
        if (sha1Hex == null || !sha1Hex.matches("[0-9a-fA-F]{40}")) {
            logger.warning("No usable SHA-1 for the resource pack — plugin pack distribution"
                    + " disabled (the hash is fetched on startup and stored in "
                    + JSON_FILE_NAME + ").");
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING,
                    "resource-pack.json url is not a valid URL — plugin pack distribution disabled.", e);
            return null;
        }
        // Stable, URL+hash-derived id so repeat requests refer to the same pack entry.
        UUID packId = UUID.nameUUIDFromBytes(
                (url.trim() + "|" + sha1Hex.toLowerCase(java.util.Locale.ROOT))
                        .getBytes(StandardCharsets.UTF_8));
        this.packId = packId;
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                .id(packId)
                .uri(uri)
                .hash(sha1Hex.toLowerCase(java.util.Locale.ROOT))
                .build();
        return ResourcePackRequest.resourcePackRequest()
                .replace(true)
                .packs(info)
                .prompt(Component.text(prompt))
                .required(required())
                .build();
    }

    /**
     * <b>Startup hash resolution.</b> The zip at the configured URL is downloaded and hashed
     * (asynchronously) on every start / reload, because the announced SHA-1 must match the
     * published file exactly — a drifted hash makes every client reject the pack, which is the
     * breakage this class used to suffer from. The resolved hash is persisted into
     * {@code resource-pack.json} and the pack is (re-)pushed to everyone online. An unreachable
     * URL keeps the hash from the previous start.
     */
    private void resolveHashFromUrl() {
        if (!enabled()) {
            return;
        }
        String url = configuredUrl();
        if (url == null || url.isBlank() || !url.startsWith("http")) {
            return; // file:-style or blank URLs cannot be hashed from here
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String actual = fetchSha1(url);
            if (actual == null) {
                if (liveSha1 == null && jsonSha1 == null) {
                    logger.warning("Could not download the resource pack at " + url
                            + " to resolve its SHA-1 — no pack is sent until a hash is known.");
                } else {
                    logger.info(() -> "Could not reach " + url
                            + " — using the hash stored in " + JSON_FILE_NAME + ".");
                }
                return;
            }
            if (actual.equalsIgnoreCase(liveSha1)) {
                return;
            }
            logger.info(() -> "Resource pack SHA-1 resolved from " + url + ": " + actual);
            liveSha1 = actual;
            if (!actual.equalsIgnoreCase(jsonSha1)) {
                saveJson(url, actual);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                ResourcePackRequest built = buildRequest();
                if (built == null) {
                    return;
                }
                this.request = built;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    applyTo(online);
                }
            });
        });
    }

    /**
     * Downloads the zip at {@code url} and returns its SHA-1 as 40 lowercase hex chars, or
     * {@code null} when the fetch fails / hash cannot be computed. Runs off the main thread.
     */
    private String fetchSha1(String url) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(true);
            // GitHub serves release assets from a signed redirect and does not care about the
            // agent, but some proxies/object stores do - be explicit instead of "Java/21".
            conn.setRequestProperty("User-Agent", "RumilancePractice/1.2");
            if (conn.getResponseCode() >= 400) {
                return null;
            }
            // A wrong hash is worse than no hash: the client would download the real pack, fail
            // the check and end up with no badge at all ("リソパが読み込めない"). So refuse to hash
            // anything that is obviously not the pack - an HTML error/landing page, or nothing.
            String contentType = conn.getContentType();
            if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("text/html")) {
                return null;
            }
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            long total = 0L;
            try (java.io.InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[16 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    digest.update(buf, 0, read);
                    total += read;
                }
            }
            if (total == 0L) {
                return null;
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(40);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
