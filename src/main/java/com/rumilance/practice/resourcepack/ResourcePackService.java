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
 * needed. Every player receives the pack shortly after joining
 * ({@code resource-pack.*} in config.yml controls URL, hash and behaviour).
 *
 * <p><b>Policy</b> (choosable in the admin GUI, persisted to {@code pack-policy.yml}):
 * <ul>
 *   <li><b>required</b> — players who decline the pack or whose download fails are kicked
 *       (the classic behaviour),</li>
 *   <li><b>recommended</b> (default) — players may decline and keep playing; the rank badges
 *       then fall back to plain-text prefixes ({@code N} / {@code N+} / {@code OWNER}) on
 *       their client because the custom-font glyphs only render with the pack installed.</li>
 * </ul>
 * The service tracks who actually applied the pack ({@link #hasPack(Player)}) so the
 * scoreboard / TAB prefix layers can pick glyphs vs. text badges per viewer.</p>
 *
 * <p>Uses Paper's Adventure resource-pack API ({@link ResourcePackRequest}); the client
 * verifies the pack by its SHA-1 hash, which must match the shipped zip exactly
 * ({@code dist/RumilanceResourcePack.sha1}). If the URL or hash in config.yml is malformed
 * the service logs a warning and simply sends nothing (players are never kicked because of
 * an admin typo).</p>
 *
 * <p>Note: when this service is enabled, remove any {@code resource-pack*} lines from
 * server.properties — otherwise clients may be asked to apply the pack twice.</p>
 */
public final class ResourcePackService implements Listener {

    /** Shipped default: the repo's dist/ zip (public repository). */
    private static final String DEFAULT_URL =
            "https://raw.githubusercontent.com/ifuto/RumilancePractice/"
                    + "arena/01a06257-rumilancepractice/dist/RumilanceResourcePack.zip";
    private static final String DEFAULT_SHA1 = "c4cf82d41ba0564aaac4752cb6a95225e768b2e2";

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
    /** Admin-GUI policy override ({@code pack-policy.yml}); null = use config.yml default. */
    private volatile Boolean requiredOverride;

    public ResourcePackService(Plugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.logger = plugin.getLogger();
        loadPolicy();
        reload();
    }

    /**
     * Re-reads {@code resource-pack.*} from config.yml and pushes the fresh pack to all
     * online players (clients that already applied it skip the download thanks to the
     * unchanged URL + SHA-1 pair). Safe to call from {@code /rumireload}.
     */
    public void reload() {
        this.request = buildRequest();
        ResourcePackRequest built = this.request;
        if (built == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            applyTo(online);
        }
    }

    /** Whether plugin-side distribution is enabled at all. */
    public boolean enabled() {
        return configService.config().getBoolean("resource-pack.enabled", true);
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
     * Whether the player's client actually applied a resource pack (our glyphs render).
     * Unknown state (fresh join, download in flight) falls back to the vanilla check, which
     * is {@code false} until the pack finishes loading — viewers briefly see text badges.
     */
    public boolean hasPack(Player player) {
        if (player == null) {
            return false;
        }
        Boolean known = packApplied.get(player.getUniqueId());
        if (known != null) {
            return known;
        }
        return player.hasResourcePack();
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

    /** Sends the pack to the player (no-op when disabled or misconfigured). */
    public void applyTo(Player player) {
        ResourcePackRequest toSend = this.request;
        if (toSend == null) {
            return;
        }
        player.sendResourcePacks(toSend);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (this.request == null) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applyTo(player);
            }
        }, APPLY_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        failedAttempts.remove(event.getPlayer().getUniqueId());
        packApplied.remove(event.getPlayer().getUniqueId());
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
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        UUID ourId = this.packId;
        if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            if (ourId != null && ourId.equals(event.getID())) {
                failedAttempts.remove(player.getUniqueId());
                packApplied.put(player.getUniqueId(), Boolean.TRUE);
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
        // The pack is already applied — any failure status is stale/duplicate noise.
        if (player.hasResourcePack()) {
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
                "This server requires the Rumilance resource pack.");
        logger.info(() -> "Kicking " + player.getName()
                + " — resource pack " + status.name().toLowerCase(java.util.Locale.ROOT));
        player.kick(Component.text(message));
    }

    /**
     * Builds the pack request from config, or {@code null} when disabled/misconfigured
     * (a warning is logged for the latter so admins can spot the problem).
     */
    private ResourcePackRequest buildRequest() {
        if (!enabled()) {
            return null;
        }
        String url = configService.config().getString("resource-pack.url", DEFAULT_URL);
        String sha1Hex = configService.config().getString("resource-pack.sha1", DEFAULT_SHA1);
        String prompt = configService.config().getString("resource-pack.prompt",
                "Required for Rumilance Practice icons.");
        if (url == null || url.isBlank()) {
            logger.warning("resource-pack.url is empty — plugin pack distribution disabled.");
            return null;
        }
        if (sha1Hex == null || !sha1Hex.matches("[0-9a-fA-F]{40}")) {
            logger.warning("resource-pack.sha1 must be exactly 40 hex characters "
                    + "(see dist/RumilanceResourcePack.sha1) — plugin pack distribution disabled.");
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING,
                    "resource-pack.url is not a valid URL — plugin pack distribution disabled.", e);
            return null;
        }
        // Stable, config-derived id so repeat requests refer to the same pack entry.
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
}
