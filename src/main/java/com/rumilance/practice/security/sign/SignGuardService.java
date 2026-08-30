package com.rumilance.practice.security.sign;

import com.rumilance.practice.ban.BanDuration;
import com.rumilance.practice.ban.BanService;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.AuditLogRepository;
import com.rumilance.practice.model.AuditLogEntry;
import com.rumilance.practice.util.AsyncExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Anti-exploit guard for sign edits. Scans each line's component tree and, when a malicious
 * translation-key / keybind / click-event structure is detected, cancels the edit, logs it, and
 * (for the highest-confidence structures) auto-bans — mirroring how public servers like DonutSMP
 * shut down sign translation-key exploits. Suspicious-but-ambiguous edits are blocked and logged
 * without a ban to keep false positives harmless.
 */
public final class SignGuardService {

    private final ConfigService configService;
    private final BanService banService;
    private final AuditLogRepository auditLogRepository;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;

    public SignGuardService(ConfigService configService, BanService banService,
                            AuditLogRepository auditLogRepository, AsyncExecutor asyncExecutor, Logger logger) {
        this.configService = configService;
        this.banService = banService;
        this.auditLogRepository = auditLogRepository;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    public boolean enabled() {
        return configService.config().getBoolean("sign-guard.enabled", true);
    }

    private SignComponentScanner scanner() {
        int depth = configService.config().getInt("sign-guard.max-nest-depth", 10);
        List<String> allowed = configService.config().getStringList("sign-guard.allowed-translatable-prefixes");
        if (allowed.isEmpty()) {
            allowed = List.of("block.minecraft.", "item.minecraft.", "entity.minecraft.");
        }
        List<String> critical = configService.config().getStringList("sign-guard.critical-keys");
        List<String> criticalPrefixes = configService.config()
                .getStringList("sign-guard.critical-key-prefixes");
        return new SignComponentScanner(depth, allowed, critical, criticalPrefixes);
    }

    /**
     * Inspects the sign lines. Returns true if the edit must be cancelled. Auto-ban (if enabled)
     * and audit logging are handled here.
     *
     * @return true when the edit should be cancelled.
     */
    public boolean inspect(Player player, List<Component> lines, Location location) {
        if (!enabled()) {
            return false;
        }
        if (player.hasPermission("rumilance.admin") || player.hasPermission("rumilance.sign.bypass")) {
            return false;
        }
        SignComponentScanner scanner = scanner();
        SignScanResult worst = SignScanResult.CLEAN;
        for (Component line : lines) {
            worst = worst.max(scanner.scan(line));
        }
        if (!worst.isBlocked()) {
            return false;
        }
        SignScanResult result = worst;
        audit(player, location, result);
        if (configService.config().getBoolean("sign-guard.notify-player", true)) {
            player.sendMessage(Component.text("その看板の内容は許可されていません。", NamedTextColor.RED));
        }
        if (result.isMalicious() && configService.config().getBoolean("sign-guard.auto-ban", true)) {
            autoBan(player, result);
        }
        return true;
    }

    private void autoBan(Player player, SignScanResult result) {
        String token = configService.config().getString("sign-guard.auto-ban-duration", "auto");
        Duration duration;
        String durationToken;
        if (token == null || token.equalsIgnoreCase("auto")) {
            int offense = banService.banCount(player.getUniqueId()) + 1;
            duration = BanDuration.forOffenseNumber(offense);
            durationToken = BanDuration.autoToken(offense);
        } else {
            duration = BanDuration.parse(token).orElse(null);
            durationToken = token;
        }
        String reason = "Sign exploit (" + result.reason() + ")";
        banService.ban(player.getUniqueId(), player.getName(), reason, duration,
                durationToken == null ? "auto" : durationToken, "SignGuard");
    }

    private void audit(Player player, Location location, SignScanResult result) {
        String details = "sev=" + result.severity() + " reason=" + result.reason()
                + " loc=" + describe(location);
        asyncExecutor.execute(() -> {
            try {
                auditLogRepository.insert(AuditLogEntry.of(player.getUniqueId(), "SIGN_GUARD_BLOCK", details));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to log sign guard event", e);
            }
        });
    }

    private static String describe(Location location) {
        if (location == null || location.getWorld() == null) {
            return "?";
        }
        return location.getWorld().getName() + ":" + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }
}
