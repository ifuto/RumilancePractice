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
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Distributes the server resource pack to players directly from the plugin — no
 * {@code resource-pack=} / {@code require-resource-pack=} entries in server.properties are
 * needed. Every player receives the pack shortly after joining
 * ({@code resource-pack.*} in config.yml controls URL, hash and behaviour) and, when the
 * pack is marked {@code required}, players who decline it — or whose download fails — are
 * kicked, because the rank-badge glyphs ({@code rumilance:icons}) only render with the pack
 * installed.
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
    private static final String DEFAULT_SHA1 = "730f0e2399135601333404476b5053ab51c483d9";

    /** Small delay after join so login-time packets settle before the pack prompt. */
    private static final long APPLY_DELAY_TICKS = 10L;

    private final Plugin plugin;
    private final ConfigService configService;
    private final Logger logger;

    /** Cached request; rebuilt by {@link #reload()} (null = disabled or misconfigured). */
    private volatile ResourcePackRequest request;
    /** Id of our pack — used to recognise our own status events (and keep us on top). */
    private volatile UUID packId;

    public ResourcePackService(Plugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.logger = plugin.getLogger();
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

    /** Whether players refusing/failing the pack must be kicked. */
    public boolean required() {
        return configService.config().getBoolean("resource-pack.required", true);
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

    /**
     * Kicks players who refuse or fail the pack when it is required. DECLINED = the player
     * pressed "No" in the pack dialog; FAILED_DOWNLOAD / INVALID_URL = the client never got
     * the pack, which is equally unusable for this server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        // Keep OUR pack pinned to the top of the client's Selected list: whenever some other
        // pack (another plugin, a /pack command...) finishes applying, re-send ours so it is
        // the most recent pack again and therefore stays on top. Our own SUCCESS events are
        // recognised by the stable pack id and never re-trigger this (no loop).
        UUID ourId = this.packId;
        if (this.request != null && ourId != null
                && event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESS
                && !ourId.equals(event.getID())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    applyTo(player);
                }
            });
        }
        if (this.request == null || !required()) {
            return;
        }
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status == PlayerResourcePackStatusEvent.Status.DECLINED
                || status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || status == PlayerResourcePackStatusEvent.Status.INVALID_URL) {
            String message = configService.config().getString("resource-pack.kick-message",
                    "This server requires the Rumilance resource pack.");
            logger.info(() -> "Kicking " + player.getName()
                    + " — resource pack " + status.name().toLowerCase(java.util.Locale.ROOT));
            player.kick(Component.text(message));
        }
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
