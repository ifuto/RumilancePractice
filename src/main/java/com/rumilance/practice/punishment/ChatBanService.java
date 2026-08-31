package com.rumilance.practice.punishment;

import com.rumilance.practice.database.repository.AuditLogRepository;
import com.rumilance.practice.database.repository.ObjectionRepository;
import com.rumilance.practice.database.repository.PunishmentRepository;
import com.rumilance.practice.model.AuditLogEntry;
import com.rumilance.practice.model.PunishmentRecord;
import com.rumilance.practice.util.AsyncExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ChatBan / objection handling. Disconnect ChatBan is skipped during plugin shutdown.
 */
public final class ChatBanService {

    private final PunishmentRepository punishmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectionRepository objectionRepository;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;
    private final Duration defaultDisconnectBan;
    private final Plugin plugin;
    private final Map<UUID, UUID> activeChatBan = new ConcurrentHashMap<>();
    private final Map<UUID, PunishmentRecord> activeChatBanRecords = new ConcurrentHashMap<>();
    private final Set<UUID> objectedBans = ConcurrentHashMap.newKeySet();
    /**
     * Players banned while OFFLINE get a notification the next time they join. We mark the ban
     * record id as "pending login notice" when a staff member bans an offline target.
     */
    private final Set<UUID> pendingLoginNotice = ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown;

    public ChatBanService(
            PunishmentRepository punishmentRepository,
            AuditLogRepository auditLogRepository,
            ObjectionRepository objectionRepository,
            AsyncExecutor asyncExecutor,
            Logger logger,
            Duration defaultDisconnectBan
    ) {
        this(punishmentRepository, auditLogRepository, objectionRepository, asyncExecutor,
                logger, defaultDisconnectBan, null);
    }

    public ChatBanService(
            PunishmentRepository punishmentRepository,
            AuditLogRepository auditLogRepository,
            ObjectionRepository objectionRepository,
            AsyncExecutor asyncExecutor,
            Logger logger,
            Duration defaultDisconnectBan,
            Plugin plugin
    ) {
        this.punishmentRepository = punishmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectionRepository = objectionRepository;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
        this.defaultDisconnectBan = defaultDisconnectBan;
        this.plugin = plugin;
    }

    public void setShuttingDown(boolean shuttingDown) {
        this.shuttingDown = shuttingDown;
    }

    public void issueDisconnectBan(UUID playerId, UUID matchId) {
        if (shuttingDown) {
            return;
        }
        issue(playerId, null, "CHATBAN", "Combat disconnect match=" + matchId, defaultDisconnectBan);
    }

    public void issue(UUID target, UUID staff, String type, String reason, Duration duration) {
        issue(target, staff, type, reason, duration, false);
    }

    /**
     * Issues a chat ban. If the target is online they are notified immediately in chat; if they
     * are offline a notice is delivered the next time they join (see {@link #notifyOnJoin}).
     */
    public void issueWithNotice(UUID target, UUID staff, String type, String reason, Duration duration) {
        Instant now = Instant.now();
        PunishmentRecord record = new PunishmentRecord(
                UUID.randomUUID(), target, staff, type, reason, now,
                duration == null ? null : now.plus(duration), false
        );
        cacheRecord(record);
        Player online = org.bukkit.Bukkit.getPlayer(target);
        if (online != null && online.isOnline()) {
            notifyBanned(online, record);
        } else {
            // Offline target: remember to tell them the moment they log back in.
            pendingLoginNotice.add(target);
        }
        asyncExecutor.execute(() -> {
            try {
                punishmentRepository.insert(record);
                auditLogRepository.insert(AuditLogEntry.of(staff, "CHATBAN",
                        "target=" + target + " reason=" + reason + " id=" + record.id()
                                + (online == null ? " offline=1" : "")));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to persist chatban", e);
            }
        });
    }

    /**
     * Delivers any pending chat-ban notice to a player who has just joined. Called on join; reads
     * the active ban from cache (warmed by {@link #warmCache}) or the repository, then clears the
     * pending flag so it is shown only once.
     */
    public void notifyOnJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        // Only auto-notify when this ban was issued while the player was offline, OR the player
        // currently has an active ban they may not have seen (e.g. banned then rejoined).
        boolean pending = pendingLoginNotice.remove(id);
        if (!pending) {
            return;
        }
        asyncExecutor.execute(() -> {
            try {
                Instant now = Instant.now();
                PunishmentRecord record = activeChatBanRecords.get(id);
                if (record == null || !record.isActive(now)) {
                    for (PunishmentRecord r : punishmentRepository.findActiveForPlayer(id)) {
                        if ("CHATBAN".equalsIgnoreCase(r.type()) && r.isActive(now)) {
                            record = r;
                            cacheRecord(r);
                            break;
                        }
                    }
                }
                final PunishmentRecord found = record;
                Runnable send = () -> {
                    if (found != null && player.isOnline() && found.isActive(Instant.now())) {
                        notifyBanned(player, found);
                    }
                };
                if (plugin != null && Bukkit.isPrimaryThread()) {
                    send.run();
                } else if (plugin != null) {
                    Bukkit.getScheduler().runTask(plugin, send);
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "join chatban notice lookup failed", e);
            }
        });
    }

    /** Sends the "you are chat-banned" notice to an online target. */
    private void notifyBanned(Player player, PunishmentRecord record) {
        String expires = record.expiresAt() == null
                ? "permanent"
                : record.expiresAt().toString();
        player.sendMessage(Component.text("You have been ChatBanned.", NamedTextColor.RED).decoration(
                net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        player.sendMessage(Component.text("Reason: ", NamedTextColor.RED)
                .append(Component.text(record.reason() == null ? "-" : record.reason(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Until: ", NamedTextColor.RED)
                .append(Component.text(expires, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Use /objection <reason> to appeal.", NamedTextColor.GRAY));
    }

    /**
     * @param announce reserved for callers that previously broadcasted; persistence is unchanged
     */
    public void issue(UUID target, UUID staff, String type, String reason, Duration duration, boolean announce) {
        // Route through the notifying variant so online targets get told instantly and offline
        // targets get told on their next join.
        issueWithNotice(target, staff, type, reason, duration);
    }

    public boolean isChatBanned(UUID uuid) {
        PunishmentRecord cached = activeChatBanRecords.get(uuid);
        Instant now = Instant.now();
        if (cached != null) {
            if (cached.isActive(now)) {
                return true;
            }
            evictCache(uuid);
        }
        try {
            for (PunishmentRecord record : punishmentRepository.findActiveForPlayer(uuid)) {
                if ("CHATBAN".equalsIgnoreCase(record.type()) && record.isActive(now)) {
                    cacheRecord(record);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return activeChatBan.containsKey(uuid);
        }
    }

    public Optional<UUID> activeBanId(UUID uuid) {
        return Optional.ofNullable(activeChatBan.get(uuid));
    }

    public void unban(UUID targetOrBanId) {
        asyncExecutor.execute(() -> {
            try {
                Optional<PunishmentRecord> byId = punishmentRepository.findById(targetOrBanId);
                if (byId.isPresent()) {
                    punishmentRepository.revoke(targetOrBanId);
                    evictCache(byId.get().targetUuid());
                    return;
                }
                for (PunishmentRecord record : punishmentRepository.findActiveForPlayer(targetOrBanId)) {
                    if ("CHATBAN".equalsIgnoreCase(record.type())) {
                        punishmentRepository.revoke(record.id());
                    }
                }
                evictCache(targetOrBanId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to unban", e);
            }
        });
    }

    public void warmCache(UUID playerId) {
        asyncExecutor.execute(() -> {
            try {
                Instant now = Instant.now();
                for (PunishmentRecord record : punishmentRepository.findActiveForPlayer(playerId)) {
                    if ("CHATBAN".equalsIgnoreCase(record.type()) && record.isActive(now)) {
                        cacheRecord(record);
                        return;
                    }
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Failed to warm ChatBan cache for " + playerId, e);
            }
        });
    }

    public boolean submitObjection(Player player, String reason) {
        UUID banId = activeChatBan.get(player.getUniqueId());
        if (banId == null) {
            try {
                banId = punishmentRepository.findActiveForPlayer(player.getUniqueId()).stream()
                        .filter(p -> "CHATBAN".equalsIgnoreCase(p.type()))
                        .map(PunishmentRecord::id)
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                return false;
            }
        }
        if (banId == null) {
            player.sendMessage(Component.text("No active ChatBan.", NamedTextColor.RED));
            return false;
        }
        if (!objectedBans.add(banId)) {
            player.sendMessage(Component.text("Already objected for this ChatBan.", NamedTextColor.RED));
            return false;
        }
        UUID finalBanId = banId;
        ObjectionRepository.Objection objection = new ObjectionRepository.Objection(
                UUID.randomUUID(), finalBanId, player.getUniqueId(), reason, "PENDING", Instant.now(), null, null);
        asyncExecutor.execute(() -> {
            try {
                objectionRepository.insert(objection);
                auditLogRepository.insert(AuditLogEntry.of(player.getUniqueId(), "OBJECTION",
                        "chatban=" + finalBanId + " reason=" + reason + " objection=" + objection.id()));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to store objection", e);
            }
        });
        player.sendMessage(Component.text("Objection submitted.", NamedTextColor.GREEN));
        return true;
    }

    public void blockIfBanned(Player player, String context) {
        if (isChatBanned(player.getUniqueId()) && !player.hasPermission("rumilance.punishment.bypass")) {
            player.sendMessage(Component.text("You are ChatBanned (" + context + "). Use /objection <reason>.",
                    NamedTextColor.RED));
        }
    }

    private void cacheRecord(PunishmentRecord record) {
        activeChatBan.put(record.targetUuid(), record.id());
        activeChatBanRecords.put(record.targetUuid(), record);
    }

    private void evictCache(UUID playerId) {
        activeChatBan.remove(playerId);
        activeChatBanRecords.remove(playerId);
    }
}
