package com.rumilance.practice.punishment;

import com.rumilance.practice.database.repository.AuditLogRepository;
import com.rumilance.practice.database.repository.ObjectionRepository;
import com.rumilance.practice.database.repository.PunishmentRepository;
import com.rumilance.practice.model.AuditLogEntry;
import com.rumilance.practice.model.PunishmentRecord;
import com.rumilance.practice.util.AsyncExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

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
    private final Map<UUID, UUID> activeChatBan = new ConcurrentHashMap<>();
    private final Map<UUID, PunishmentRecord> activeChatBanRecords = new ConcurrentHashMap<>();
    private final Set<UUID> objectedBans = ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown;

    public ChatBanService(
            PunishmentRepository punishmentRepository,
            AuditLogRepository auditLogRepository,
            ObjectionRepository objectionRepository,
            AsyncExecutor asyncExecutor,
            Logger logger,
            Duration defaultDisconnectBan
    ) {
        this.punishmentRepository = punishmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectionRepository = objectionRepository;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
        this.defaultDisconnectBan = defaultDisconnectBan;
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
     * @param announce reserved for callers that previously broadcasted; persistence is unchanged
     */
    public void issue(UUID target, UUID staff, String type, String reason, Duration duration, boolean announce) {
        Instant now = Instant.now();
        PunishmentRecord record = new PunishmentRecord(
                UUID.randomUUID(), target, staff, type, reason, now,
                duration == null ? null : now.plus(duration), false
        );
        cacheRecord(record);
        asyncExecutor.execute(() -> {
            try {
                punishmentRepository.insert(record);
                auditLogRepository.insert(AuditLogEntry.of(staff, "CHATBAN",
                        "target=" + target + " reason=" + reason + " id=" + record.id()
                                + (announce ? " announce=1" : "")));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to persist chatban", e);
            }
        });
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
