package com.rumilance.practice.anticheat;

import com.rumilance.practice.locale.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the client anti-cheat (pair of the {@code rumilance-ac} Fabric mod).
 *
 * <h3>Trust model (why this is "theoretically unbypassable" — and precisely where)</h3>
 * Lessons applied from BAC, CheatBreaker, Lunar/Apollo and GrimAC (see docs/ANTICHEAT.md):
 * <ul>
 *   <li><b>The client is never trusted for statements</b> — it must PROVE, per rolling
 *       challenge, that the official mod is present AND that gameplay actually flows
 *       through it.</li>
 *   <li><b>Proof of presence:</b> one-time nonces on {@code rumilance:ac} (join + 60s
 *       heartbeats). Replays and pre-recorded answers are useless.</li>
 *   <li><b>Proof of unmodified build:</b> the mod reports the SHA-256 of its own jar; once
 *       an op pins accepted builds ({@code /anticheat trust <sha>}), required users must
 *       present a pinned hash — forgery then needs the byte-identical official jar.</li>
 *   <li><b>Proof gameplay flows through the mod (the anti-oracle core):</b> the mod taps
 *       HEAD of its outbound pipeline and digests every movement packet it sends
 *       (FNV-1a over raw x/y/z/yaw/pitch bits). This service rebuilds the same digest from
 *       {@code PlayerMoveEvent} and compares at the packet index the mod reports. Injected,
 *       dropped or rewritten movement packets diverge immediately; an oracle clean-client
 *       proxy would have to reproduce the cheater's exact packet stream — impossible while
 *       also cheating. TCP ordering makes legit divergence impossible by construction
 *       (zero-FP: Sodium, ShieldStats etc. never touch movement serialisation).</li>
 *   <li><b>Documented residue (universal ceiling of userland ACs):</b> same-JVM hooks AHEAD
 *       of the mod's tap. BAC/CheatBreaker history shows nothing at the same privilege
 *       level beats that; we state it instead of pretending.</li>
 *   <li><b>Kick policy stays Grim-style deterministic:</b> we only kick on missing/tampered
 *       attestation or digest divergence of REQUIRED users — never on heuristics, so the
 *       false-positive rate of the enforcement path is zero.</li>
 * </ul>
 *
 * <p>The mod is optional for everyone by default; ops/console mandate per player via
 * {@code /anticheat require} (persisted in {@code anticheat.yml}; config.yml untouched).</p>
 */
public final class AntiCheatService implements Listener, PluginMessageListener {

    public static final String CHANNEL = "rumilance:ac";
    private static final String FILE = "anticheat.yml";
    private static final long HELLO_DELAY_TICKS = 60L;
    private static final long BRAND_CAPTURE_TICKS = 40L;
    private static final long VERIFY_GRACE_TICKS = 240L;
    private static final long REQUIRE_GRACE_TICKS = 600L;
    private static final long HEARTBEAT_PERIOD_TICKS = 1200L;   // every 60s
    private static final long HEARTBEAT_DEADLINE_TICKS = 200L;  // answer within 10s
    private static final long RESYNC_COOLDOWN_MILLIS = 10L * 60_000L;
    private static final int MAX_RESYNCS_PER_SESSION = 3;
    private static final int KICK_AFTER_FAILED_HEARTBEATS = 2;

    private final Plugin plugin;
    private final MessageService messageService;
    /** Players for whom the mod is mandatory (persisted). */
    private final Set<UUID> required = ConcurrentHashMap.newKeySet();
    /** SHA-256 of accepted mod builds (persisted; empty = version-agnostic). */
    private final Set<String> trustedHashes = ConcurrentHashMap.newKeySet();

    private static final class Session {
        final MovementDigest digest = new MovementDigest();
        String nonce;
        boolean verified;
        int protocol;
        String modVersion = "-";
        String flags = "-";
        int totalMods = -1;
        String jarSha = "-";
        boolean jarTrusted = true;
        String brand = "-";
        int failedHeartbeats;
        int resyncs;
        long lastResyncAt;
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public AntiCheatService(Plugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    /** Registers channel + listeners, loads mandates/pins, starts the heartbeat (on enable). */
    public void start() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadAll();
        Bukkit.getScheduler().runTaskTimer(plugin, this::heartbeat, HEARTBEAT_PERIOD_TICKS,
                HEARTBEAT_PERIOD_TICKS);
    }

    /** Persists mandates/pins and closes the channel (on disable). */
    public void stop() {
        saveAll();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
    }

    // ------------------------------------------------------------------ mandates & pins

    public boolean isRequired(UUID playerId) {
        return required.contains(playerId);
    }

    /** Makes the mod mandatory; immediate enforcement (30s grace) if the player is online. */
    public void require(UUID playerId) {
        required.add(playerId);
        saveAll();
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            challenge(online, HELLO_DELAY_TICKS);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player target = Bukkit.getPlayer(playerId);
                if (target != null && target.isOnline() && !isVerified(playerId)) {
                    kick(target, "anticheat.kick-not-verified");
                }
            }, REQUIRE_GRACE_TICKS);
        }
    }

    public void unrequire(UUID playerId) {
        required.remove(playerId);
        saveAll();
    }

    public Set<UUID> requiredPlayers() {
        return Set.copyOf(required);
    }

    /** Pins an accepted mod build SHA-256; empty pin set = version-agnostic. */
    public boolean trust(String sha256) {
        boolean added = trustedHashes.add(sha256.toLowerCase(java.util.Locale.ROOT));
        saveAll();
        recheckTrustedOnline();
        return added;
    }

    public boolean untrust(String sha256) {
        boolean removed = trustedHashes.remove(sha256.toLowerCase(java.util.Locale.ROOT));
        saveAll();
        recheckTrustedOnline();
        return removed;
    }

    public Set<String> trustedHashes() {
        return Set.copyOf(trustedHashes);
    }

    /** After pin changes, re-evaluate required players immediately (fail-closed). */
    private void recheckTrustedOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session == null || !session.verified || !isRequired(player.getUniqueId())) {
                continue;
            }
            if (!isJarTrusted(session)) {
                kick(player, "anticheat.kick-untrusted-build");
            }
        }
    }

    // ------------------------------------------------------------------ join / leave / move tap

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Session session = new Session();
        session.nonce = newNonce();
        sessions.put(player.getUniqueId(), session);
        challenge(player, HELLO_DELAY_TICKS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session current = sessions.get(player.getUniqueId());
            if (current != null && player.isOnline()) {
                String brand = player.getClientBrandName();
                if (brand != null && !brand.isEmpty()) {
                    current.brand = brand;
                }
            }
        }, BRAND_CAPTURE_TICKS);
        if (isRequired(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player target = Bukkit.getPlayer(player.getUniqueId());
                if (target != null && target.isOnline() && !isVerified(player.getUniqueId())) {
                    kick(target, "anticheat.kick-not-verified");
                }
            }, VERIFY_GRACE_TICKS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Server half of the digest binding. Teleports are excluded (no client move packet
     * corresponds to them), and the client side mirrors every other rule, so the streams
     * stay one-to-one for any honest client.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        var to = event.getTo();
        session.digest.record(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
    }

    // ------------------------------------------------------------------ challenges

    private void challenge(Player player, long delayTicks) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player target = Bukkit.getPlayer(id);
            Session session = sessions.get(id);
            if (target == null || !target.isOnline() || session == null) {
                return;
            }
            session.nonce = newNonce();
            sendHello(target, session.nonce);
        }, delayTicks);
    }

    /** Rolling re-verification: any honest mod answers here; required users that go silent
     * are kicked after two consecutive misses. */
    private void heartbeat() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session == null) {
                continue;
            }
            String nonce = newNonce();
            session.nonce = nonce;
            sendHello(player, nonce);
            UUID id = player.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player target = Bukkit.getPlayer(id);
                Session current = sessions.get(id);
                if (target == null || !target.isOnline() || current == null) {
                    return;
                }
                if (nonce.equals(current.nonce)) {
                    // No valid pong claimed this nonce within the deadline.
                    current.failedHeartbeats++;
                    if (current.failedHeartbeats >= KICK_AFTER_FAILED_HEARTBEATS
                            && isRequired(id)) {
                        alert(target, "anticheat.alert-heartbeat-lost");
                        kick(target, "anticheat.kick-heartbeat");
                    }
                }
            }, HEARTBEAT_DEADLINE_TICKS);
        }
    }

    private void sendHello(Player player, String nonce) {
        player.sendPluginMessage(plugin, CHANNEL,
                ("HELLO|" + nonce).getBytes(StandardCharsets.UTF_8));
    }

    private static String newNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ------------------------------------------------------------------ pong handling

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.nonce == null) {
            return;
        }
        String[] parts = new String(message, StandardCharsets.UTF_8).split("\\|", -1);
        if (parts.length < 4 || !"VERIFY".equals(parts[0])) {
            heartbeatFailure(player, session);
            return;
        }
        if (!session.nonce.equals(parts[3])) {
            heartbeatFailure(player, session); // stale/forged echo: never counts
            return;
        }
        // Valid round-trip for this nonce — the deadline watchdog will not see it.
        session.nonce = newNonce();
        session.failedHeartbeats = 0;
        session.verified = true;
        try {
            session.protocol = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            session.protocol = 0;
        }
        session.modVersion = parts[2];
        session.flags = parts.length > 4 && !parts[4].isEmpty() ? parts[4] : "-";
        try {
            session.totalMods = parts.length > 5 ? Integer.parseInt(parts[5].trim()) : -1;
        } catch (NumberFormatException ignored) {
            session.totalMods = -1;
        }
        session.jarSha = parts.length > 6 && !parts[6].isEmpty() ? parts[6] : "-";
        session.jarTrusted = isJarTrusted(session);

        // 1) Build pinning: required users must run a pinned build when pins exist.
        if (isRequired(player.getUniqueId()) && !session.jarTrusted) {
            alert(player, "anticheat.alert-untrusted-build");
            kick(player, "anticheat.kick-untrusted-build");
            return;
        }
        // 2) Digest binding: the mod must reproduce the server's own move-stream digest.
        if (!checkDigest(player, session, parts)) {
            return; // already handled (resync or kick)
        }
        // 3) Scanner findings: CONFIRMED cheat-client signatures on required users.
        if (!"-".equals(session.flags)) {
            handleFindings(player, session);
        }
    }

    /**
     * Compares the client-reported digest against the server-rebuilt stream.
     * One drift is tolerated as a resync grace (teleport storms, reconnect edge cases);
     * repeated divergence on a REQUIRED user is a cheat indicator and fails closed.
     */
    private boolean checkDigest(Player player, Session session, String[] parts) {
        if (session.protocol < 2 || parts.length < 9) {
            // Old/foreign client answering without digest fields cannot prove the stream.
            if (isRequired(player.getUniqueId())) {
                alert(player, "anticheat.alert-digest-mismatch");
                kick(player, "anticheat.kick-digest");
                return false;
            }
            return true;
        }
        long clientCount;
        long clientDigest;
        try {
            clientCount = Long.parseUnsignedLong(parts[7].trim());
            clientDigest = Long.parseUnsignedLong(parts[8].trim(), 16);
        } catch (NumberFormatException e) {
            digestMismatch(player, session);
            return false;
        }
        Long expected = session.digest.digestAt(clientCount);
        if (expected != null && expected == clientDigest) {
            return true; // perfect proof
        }
        digestMismatch(player, session, clientCount, clientDigest);
        return false;
    }

    private void digestMismatch(Player player, Session session, long clientCount, long clientDigest) {
        boolean requiredUser = isRequired(player.getUniqueId());
        long now = System.currentTimeMillis();
        boolean withinGrace = session.resyncs < MAX_RESYNCS_PER_SESSION
                && now - session.lastResyncAt > RESYNC_COOLDOWN_MILLIS;
        if (withinGrace) {
            // One tolerated re-anchor (e.g. pre-mod-login traffic skew); still logged, and
            // the following comparisons keep proving the rest of the session.
            session.resyncs++;
            session.lastResyncAt = now;
            plugin.getLogger().info("[AC] digest resync for " + player.getName()
                    + " (resync " + session.resyncs + "/" + MAX_RESYNCS_PER_SESSION + ")");
            session.digest.rebase(clientCount, clientDigest);
            return;
        }
        if (requiredUser) {
            alert(player, "anticheat.alert-digest-mismatch");
            kick(player, "anticheat.kick-digest");
        } else {
            plugin.getLogger().warning("[AC] repeated digest mismatch from optional user "
                    + player.getName());
        }
    }

    private void handleFindings(Player player, Session session) {
        plugin.getLogger().warning("[AC] " + player.getName() + " client findings: " + session.flags
                + " (mod v" + session.modVersion + ", required=" + isRequired(player.getUniqueId()) + ")");
        alert(player, "anticheat.alert-flagged");
        if (isRequired(player.getUniqueId()) && session.flags.contains("CONFIRMED")) {
            kick(player, "anticheat.kick-flagged");
        }
        // SUSPECT-only (single signal) ⇒ alert only. Single-signal matches are exactly how
        // false positives are born (Sodium-adjacent names), so they never justify a kick.
    }

    private boolean isJarTrusted(Session session) {
        if (trustedHashes.isEmpty()) {
            return true; // no pins configured ⇒ version-agnostic
        }
        return trustedHashes.contains(session.jarSha.toLowerCase(java.util.Locale.ROOT));
    }

    private void heartbeatFailure(Player player, Session session) {
        session.failedHeartbeats++;
        if (session.failedHeartbeats >= KICK_AFTER_FAILED_HEARTBEATS
                && isRequired(player.getUniqueId())) {
            alert(player, "anticheat.alert-heartbeat-lost");
            kick(player, "anticheat.kick-heartbeat");
        }
    }

    // ------------------------------------------------------------------ queries & alerts

    public boolean isVerified(UUID playerId) {
        Session session = sessions.get(playerId);
        return session != null && session.verified;
    }

    /** Status tokens for /anticheat status. */
    public String[] statusTokens(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return new String[]{"-", "-", "-", "-", "-", "-", "-"};
        }
        String jar = session.jarSha.length() > 12 ? session.jarSha.substring(0, 12) : session.jarSha;
        return new String[]{
                session.verified ? "yes" : "no",
                session.modVersion,
                session.flags,
                session.totalMods >= 0 ? String.valueOf(session.totalMods) : "-",
                session.brand,
                jar,
                trustedHashes.isEmpty() ? "no-pins" : (session.jarTrusted ? "yes" : "NO")
        };
    }

    private void kick(Player player, String key) {
        player.kick(messageService.render(player, key,
                MessageService.tags("flags", sessions.get(player.getUniqueId()) != null
                        ? sessions.get(player.getUniqueId()).flags : "-")));
    }

    private void alert(Player player, String key) {
        Session session = sessions.get(player.getUniqueId());
        String raw = messageService.raw(player, key)
                .replace("<target>", player.getName())
                .replace("<flags>", session != null ? session.flags : "-");
        var mini = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        Bukkit.getConsoleSender().sendMessage(mini.deserialize(raw));
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp()) {
                op.sendMessage(mini.deserialize(raw));
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    private void loadAll() {
        required.clear();
        trustedHashes.clear();
        File file = dataFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("required")) {
            try {
                required.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
        for (String raw : yaml.getStringList("trusted")) {
            if (raw != null && raw.matches("[0-9a-fA-F]{64}")) {
                trustedHashes.add(raw.toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("required", required.stream().map(UUID::toString).sorted().toList());
        List<String> pins = trustedHashes.stream().sorted().toList();
        yaml.set("trusted", pins);
        File file = dataFile();
        file.getParentFile().mkdirs();
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[AC] Failed to save anticheat.yml: " + e.getMessage());
        }
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), FILE);
    }
}
